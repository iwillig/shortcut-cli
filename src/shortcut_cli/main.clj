(ns shortcut-cli.main
  (:gen-class)
  (:require [clj-commons.format.table :as common-table]
            [org.httpkit.client :as hk-client]
            [clojure.string :as str]
            [jsonista.core :as json]
            [clojure.java.io :as io]
            [puget.printer :as printer]
            [clj-yaml.core :as yaml]
            [cli-matic.core :as cli]
            [com.brunobonacci.mulog :as μ]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [bling.core :as bling]))

;;(repl/install-pretty-exceptions)

(def shortcut-openapi-json
  "https://developer.shortcut.com/api/rest/v3/shortcut.openapi.json")

(defn get-shortcut-token
  []
  (System/getenv "SHORTCUT_TOKEN"))

(defn load-cli-config
  []
  (yaml/parse-stream (io/reader (io/resource "cli.yml"))))

(defn load-openapi
  []
  (yaml/parse-stream (io/reader (io/resource "shortcut.openapi.json"))))

(def openapi (load-openapi))

(defrecord RouteInfo [operation-id path method route-info])

(defn routes
  []
  (mapcat identity
          (for [[path path-info] (:paths openapi)]
            (for [[method method-path] path-info]
              (->RouteInfo
               (keyword (:operationId method-path))
               path
               method
               method-path)))))

(def schemas (get-in openapi [:components :schemas]))

(def routes-by-operation-id
  (zipmap (map :operation-id (routes)) (routes)))

(defn server-url
  []
  (:url (first (get-in openapi [:servers]))))

(defn openapi-type->malli
  "Convert OpenAPI schema type to Malli schema"
  [param-schema]
  (let [type-str (:type param-schema)
        format-str (:format param-schema)]
    (case type-str
      "string" (cond
                 (= format-str "uuid") :uuid
                 (:enum param-schema) (into [:enum] (:enum param-schema))
                 (:pattern param-schema) [:re (re-pattern (:pattern param-schema))]
                 :else :string)
      "integer" [:int]
      "number" [:double]
      "boolean" [:boolean]
      "array" (if-let [items (:items param-schema)]
                [:sequential (openapi-type->malli items)]
                [:sequential :any])
      "object" [:map]
      :any)))

(defn build-param-schema
  "Build Malli schema for a single parameter"
  [param]
  (let [param-name (keyword (:name param))
        required? (:required param false)
        schema (:schema param)
        base-schema (if schema
                      (openapi-type->malli schema)
                      :any)
        description (:description param)]
    (if required?
      [param-name
       (if description
         {:description description}
         {})
       base-schema]
      [param-name
       {:optional true
        :description (or description "")}
       base-schema])))

(defn build-malli-schema
  "Build complete Malli schema for route parameters"
  [route-info]
  (let [params (:parameters (:route-info route-info))
        param-schemas (map build-param-schema params)]
    (if (seq param-schemas)
      (into [:map] param-schemas)
      [:map])))

(defn validate-params
  "Validate and coerce parameters against route schema.
   Returns {:valid? true :params coerced-params} on success
   or {:valid? false :errors humanized-errors} on failure"
  [route-info params]
  (let [schema (build-malli-schema route-info)
        ;; Try to coerce string values to expected types
        coerced-params (m/decode schema params mt/string-transformer)]
    (if (m/validate schema coerced-params)
      {:valid? true
       :params coerced-params}
      (let [explanation (m/explain schema coerced-params)
            errors (me/humanize explanation)]
        {:valid? false
         :errors errors
         :schema schema}))))

(defn print-validation-errors
  "Print validation errors in a user-friendly format"
  [route-name errors]
  (binding [*out* *err*]
    (println)
    (bling/callout
     {:type :error}
     (bling/bling [:bold "Parameter Validation Failed"]))
    (println)
    (println (bling/bling [:bold "Operation:"]) (name route-name))
    (println)
    (println (bling/bling [:bold "Errors:"]))
    (doseq [[field field-errors] errors]
      (println (str "  " (bling/bling [:yellow (name field)]) ":"))
      (if (sequential? field-errors)
        (doseq [error field-errors]
          (println (str "    - " error)))
        (println (str "    - " field-errors))))
    (println)))

(defn build-path-params
  [route-info params]
  (let [route-params (map (comp keyword :name)
                          (filter (fn [x] (= (:in x) "path"))
                                  (:parameters (:route-info route-info))))]
    (select-keys params route-params)))

(defn build-query-params
  "Extract query parameters from route info and params map"
  [route-info params]
  (let [query-param-names (map (comp keyword :name)
                               (filter #(= (:in %) "query")
                                       (:parameters (:route-info route-info))))]
    (select-keys params query-param-names)))

(defn build-body-params
  "Extract body parameters from route info and params map.
   Returns all params that are not path or query params."
  [route-info params]
  (when (get-in route-info [:route-info :requestBody])
    (let [path-params (set (map (comp keyword :name)
                                (filter #(= (:in %) "path")
                                        (:parameters (:route-info route-info)))))
          query-params (set (map (comp keyword :name)
                                 (filter #(= (:in %) "query")
                                         (:parameters (:route-info route-info)))))]
      (apply dissoc params (concat path-params query-params)))))

(defn replace-path-params
  [path path-params]
  (let [path-str (name path)
        ;; Ensure path starts with / (yaml parser may create keywords like :/api/v3/...)
        path-str (if (str/starts-with? path-str "/")
                   path-str
                   (str "/" path-str))]
    (reduce (fn [p [param-name param-value]]
              (str/replace
               p
               (str "{" (name param-name) "}")
               (str param-value)))
            path-str
            path-params)))

(defn build-request-info
  "Build HTTP request info with path, query, and body parameters"
  [route-info params]
  (let [path-params (build-path-params route-info params)
        query-params (build-query-params route-info params)
        body-params (build-body-params route-info params)
        url-part (replace-path-params (:path route-info) path-params)
        base-request {:url (str (server-url) url-part)
                      :method (:method route-info)}]
    (cond-> base-request
      (seq query-params)
      (assoc :query-params query-params)

      (seq body-params)
      (assoc :body (json/write-value-as-string body-params)
             :headers {"Content-Type" "application/json"}))))

(defn handle-response
  "Handle HTTP response with error checking and logging"
  [response]
  (let [status (:status response)]
    (cond
      ;; No status (likely connection error)
      (nil? status)
      (let [error-result {:error true
                          :status nil
                          :message "Connection failed or no response received"
                          :response response}]
        (μ/log ::http-response-error
               :status nil
               :error-message (:message error-result)
               :response response)
        error-result)

      ;; Success responses
      (and (>= status 200) (< status 300))
      (do
        (μ/log ::http-response-success
               :status status
               :has-body (boolean (:body response)))
        (if (:body response)
          (try
            (update response :body json/read-value)
            (catch Exception e
              (μ/log ::json-parse-error
                     :status status
                     :error (.getMessage e)
                     :exception e)
              (assoc response :parse-error (.getMessage e))))
          response))

      ;; Error responses
      :else
      (let [error-body (try
                         (json/read-value (:body response))
                         (catch Exception _
                           {:raw-body (:body response)}))
            error-result {:error true
                          :status status
                          :message (or (:message error-body)
                                       (get {400 "Bad Request"
                                             401 "Unauthorized - Check your SHORTCUT_TOKEN"
                                             403 "Forbidden"
                                             404 "Not Found"
                                             422 "Unprocessable Entity"
                                             429 "Rate Limit Exceeded"
                                             500 "Internal Server Error"}
                                            status
                                            "Unknown Error"))
                          :body error-body}]
        (μ/log ::http-response-error
               :status status
               :error-message (:message error-result)
               :error-body error-body)
        error-result))))

(defn invoke-route
  "Invoke a Shortcut API route by operation-id with parameters"
  ([route-name params]
   (let [token (get-shortcut-token)]
     (when-not token
       (μ/log ::missing-token :route-name route-name)
       (binding [*out* *err*]
         (println "Error: SHORTCUT_TOKEN environment variable not set"))
       (System/exit 1))

     (let [route-info (get routes-by-operation-id route-name)]
       (when-not route-info
         (μ/log ::unknown-operation :route-name route-name)
         (binding [*out* *err*]
           (println "Error: Unknown operation:" route-name))
         (System/exit 1))

       ;; Validate and coerce parameters
       (let [validation (validate-params route-info params)]
         (when-not (:valid? validation)
           (μ/log ::validation-failed
                  :operation route-name
                  :errors (:errors validation))
           (print-validation-errors route-name (:errors validation))
           (System/exit 1))

         ;; Use coerced parameters for the request
         (let [validated-params (:params validation)]
           (μ/log ::api-request-start
                  :operation route-name
                  :method (:method route-info)
                  :path (:path route-info))

           (let [request-info (build-request-info route-info validated-params)
                 result (-> (merge request-info
                                   {:headers (merge (:headers request-info)
                                                    {"Shortcut-Token" token})})
                            (hk-client/request)
                            (deref)
                            (handle-response))]
             (μ/log ::api-request-complete
                    :operation route-name
                    :status (:status result)
                    :error (:error result))
             result)))))))

(defn- print-args
  [& args]
  (println args))

(defn invoke-request
  [{:keys [url method]}]
  (-> (hk-client/request
       {:url url
        :method method
        :headers {"Shortcut-Token" (get-shortcut-token)}})
      (deref)
      (handle-response)))

(defn fetch-openapi
  []
  (invoke-request {:url shortcut-openapi-json}))

(defn write-openapi
  [data]
  (spit "resources/shortcut.openapi.json"
        (yaml/generate-string (:body data))))

(defn truncate-string [s len]
  (if (> (count s) len)
    (subs s 0 len)
    s))

(def config
  {:app {:command "shortcut-cli",
         :description "A command line tool for the Shortcut REST API",
         :version "0.0.1"}
   :global-opts []
   :commands
   [{:command "doc"
     :description "Command for exploring the Shortcut REST API"
     :subcommands [{:command "endpoint"
                    :description "Prints the description"
                    :runs (fn [{[route-name] :_arguments}]
                            (if (some? route-name)
                              (printer/cprint
                               (get routes-by-operation-id (keyword route-name)))
                              (common-table/print-table
                               [{:key :operation-id
                                 :formatter name
                                 :align :left}
                                {:key (comp :summary :route-info)
                                 :title "URL Route"
                                 :align :left}
                                {:key :method
                                 :formatter name
                                 :align :left}
                                {:key :path
                                 :align :left}]
                               (sort-by :operation-id (routes)))))}

                   {:command "schema"
                    :description "Prints the API Schema"
                    :runs (fn [{[schema-name] :_arguments}]
                            (if schema-name
                              (do
                                (println (keyword schema-name))
                                (printer/cprint
                                 (get schemas (keyword schema-name) :not-found)))
                              (common-table/print-table
                               [{:key :name
                                 :formatter name
                                 :align :left}
                                {:key :type
                                 :align :left}
                                {:key :description
                                 :align :left
                                 :formatter #(truncate-string % 100)
                                 :width 100}]
                               (map (fn [[schema-name schema]] (assoc schema :name schema-name)) schemas))))}]}

    {:command "invoke"
     :description "Invoke any API endpoint by operation-id"
     :opts [{:as "Operation ID"
             :option "operation"
             :short "o"
             :type :string
             :required true}
            {:as "Parameters (JSON)"
             :option "params"
             :short "p"
             :type :string
             :default "{}"}]
     :runs (fn [{:keys [operation params]}]
             (let [parsed-params (try
                                   (json/read-value params json/keyword-keys-object-mapper)
                                   (catch Exception e
                                     (binding [*out* *err*]
                                       (println "Error: Invalid JSON parameters:" (.getMessage e)))
                                     (System/exit 1)))
                   result (invoke-route (keyword operation) parsed-params)]
               (if (:error result)
                 (do
                   (binding [*out* *err*]
                     (println "Error:" (:message result))
                     (when (:body result)
                       (printer/cprint (:body result))))
                   (System/exit 1))
                 (printer/cprint result))))}

    {:command "story"
     :description "Manage shortcut stories"
     :subcommands [{:command "add"
                    :description "Add a new Story to Shortcut"
                    :opts [{:as "Story name"
                            :option "name"
                            :short "n"
                            :type :string
                            :required true}
                           {:as "Story description"
                            :option "description"
                            :short "d"
                            :type :string}
                           {:as "Project ID"
                            :option "project-id"
                            :short "p"
                            :type :int}
                           {:as "Story type (feature, bug, chore)"
                            :option "story-type"
                            :short "t"
                            :type :string
                            :default "feature"}
                           {:as "Epic ID"
                            :option "epic-id"
                            :short "e"
                            :type :int}
                           {:as "Iteration ID"
                            :option "iteration-id"
                            :short "i"
                            :type :int}
                           {:as "Estimate (points)"
                            :option "estimate"
                            :type :int}
                           {:as "Owner IDs (comma-separated UUIDs)"
                            :option "owner-ids"
                            :short "o"
                            :type :string}]
                    :runs (fn [{:keys [name description project-id story-type
                                       epic-id iteration-id estimate owner-ids]}]
                            (let [params (cond-> {:name name
                                                  :story_type story-type}
                                           description (assoc :description description)
                                           project-id (assoc :project_id project-id)
                                           epic-id (assoc :epic_id epic-id)
                                           iteration-id (assoc :iteration_id iteration-id)
                                           estimate (assoc :estimate estimate)
                                           owner-ids (assoc :owner_ids
                                                            (mapv str/trim
                                                                  (str/split owner-ids #","))))
                                  result (invoke-route :createStory params)]
                              (if (:error result)
                                (do
                                  (binding [*out* *err*]
                                    (println "Error creating story:" (:message result))
                                    (when (:body result)
                                      (printer/cprint (:body result))))
                                  (System/exit 1))
                                (do
                                  (println "Story created successfully!")
                                  (printer/cprint result)))))}

                   {:command "view"
                    :description "Views a Story in Shortcut"
                    :runs (fn [{arguments :_arguments}]
                            (let [story-id (first arguments)]
                              (printer/cprint
                               (invoke-route :getStory {:story-public-id story-id}))))}

                   {:command "list"
                    :description "List Stories in Shortcut"
                    :runs (fn [_]
                            (printer/cprint
                             (invoke-route :listStories {})))}

                   {:command "delete"
                    :description "Deletes a Story in your Shortcut Account"
                    :runs (fn [{arguments :_arguments}]
                            (let [story-id (first arguments)]
                              (when-not story-id
                                (binding [*out* *err*]
                                  (println "Error: Story ID required"))
                                (System/exit 1))
                              (let [result (invoke-route :deleteStory {:story-public-id story-id})]
                                (if (:error result)
                                  (do
                                    (binding [*out* *err*]
                                      (println "Error deleting story:" (:message result))
                                      (when (:body result)
                                        (printer/cprint (:body result))))
                                    (System/exit 1))
                                  (println "Story" story-id "deleted successfully!")))))}]}

    {:command "epic"
     :description "Manage shortcut epics"
     :subcommands [{:command "list"
                    :description "List all Epics"
                    :runs (fn [_]
                            (printer/cprint
                             (invoke-route :listEpics {})))}

                   {:command "view"
                    :description "View an Epic by ID"
                    :runs (fn [{arguments :_arguments}]
                            (let [epic-id (first arguments)]
                              (printer/cprint
                               (invoke-route :getEpic {:epic-public-id epic-id}))))}

                   {:command "create"
                    :description "Create a new Epic"
                    :opts [{:as "Epic name"
                            :option "name"
                            :short "n"
                            :type :string
                            :required true}
                           {:as "Epic description"
                            :option "description"
                            :short "d"
                            :type :string}
                           {:as "State (to do, in progress, done)"
                            :option "state"
                            :short "s"
                            :type :string
                            :default "to do"}
                           {:as "Milestone ID"
                            :option "milestone-id"
                            :short "m"
                            :type :int}
                           {:as "Owner IDs (comma-separated UUIDs)"
                            :option "owner-ids"
                            :short "o"
                            :type :string}]
                    :runs (fn [{:keys [name description state milestone-id owner-ids]}]
                            (let [params (cond-> {:name name
                                                  :state state}
                                           description (assoc :description description)
                                           milestone-id (assoc :milestone_id milestone-id)
                                           owner-ids (assoc :owner_ids
                                                            (mapv str/trim
                                                                  (str/split owner-ids #","))))
                                  result (invoke-route :createEpic params)]
                              (if (:error result)
                                (do
                                  (binding [*out* *err*]
                                    (println "Error creating epic:" (:message result))
                                    (when (:body result)
                                      (printer/cprint (:body result))))
                                  (System/exit 1))
                                (do
                                  (println "Epic created successfully!")
                                  (printer/cprint result)))))}

                   {:command "update"
                    :description "Update an Epic"
                    :opts [{:as "Epic ID"
                            :option "id"
                            :type :int
                            :required true}
                           {:as "Epic name"
                            :option "name"
                            :short "n"
                            :type :string}
                           {:as "Epic description"
                            :option "description"
                            :short "d"
                            :type :string}
                           {:as "State (to do, in progress, done)"
                            :option "state"
                            :short "s"
                            :type :string}
                           {:as "Archived"
                            :option "archived"
                            :short "a"
                            :type :with-flag}]
                    :runs (fn [{:keys [id name description state archived]}]
                            (let [params (cond-> {:epic-public-id id}
                                           name (assoc :name name)
                                           description (assoc :description description)
                                           state (assoc :state state)
                                           (some? archived) (assoc :archived archived))
                                  result (invoke-route :updateEpic params)]
                              (if (:error result)
                                (do
                                  (binding [*out* *err*]
                                    (println "Error updating epic:" (:message result))
                                    (when (:body result)
                                      (printer/cprint (:body result))))
                                  (System/exit 1))
                                (do
                                  (println "Epic updated successfully!")
                                  (printer/cprint result)))))}

                   {:command "delete"
                    :description "Delete an Epic"
                    :runs (fn [{arguments :_arguments}]
                            (let [epic-id (first arguments)]
                              (printer/cprint
                               (invoke-route :deleteEpic {:epic-public-id epic-id}))))}

                   {:command "stories"
                    :description "List Stories in an Epic"
                    :runs (fn [{arguments :_arguments}]
                            (let [epic-id (first arguments)]
                              (printer/cprint
                               (invoke-route :listEpicStories {:epic-public-id epic-id}))))}]}

    {:command "iteration"
     :description "Manage shortcut iterations"
     :subcommands [{:command "list"
                    :description "List all Iterations"
                    :runs (fn [_]
                            (printer/cprint
                             (invoke-route :listIterations {})))}

                   {:command "view"
                    :description "View an Iteration by ID"
                    :runs (fn [{arguments :_arguments}]
                            (let [iteration-id (first arguments)]
                              (printer/cprint
                               (invoke-route :getIteration {:iteration-public-id iteration-id}))))}

                   {:command "create"
                    :description "Create a new Iteration"
                    :runs print-args}

                   {:command "update"
                    :description "Update an Iteration"
                    :runs print-args}

                   {:command "delete"
                    :description "Delete an Iteration"
                    :runs (fn [{arguments :_arguments}]
                            (let [iteration-id (first arguments)]
                              (printer/cprint
                               (invoke-route :deleteIteration {:iteration-public-id iteration-id}))))}

                   {:command "stories"
                    :description "List Stories in an Iteration"
                    :runs (fn [{arguments :_arguments}]
                            (let [iteration-id (first arguments)]
                              (printer/cprint
                               (invoke-route :listIterationStories {:iteration-public-id iteration-id}))))}]}

    {:command "milestone"
     :description "Manage shortcut milestones"
     :subcommands [{:command "list"
                    :description "List all Milestones"
                    :runs (fn [_]
                            (printer/cprint
                             (invoke-route :listMilestones {})))}

                   {:command "view"
                    :description "View a Milestone by ID"
                    :runs (fn [{arguments :_arguments}]
                            (let [milestone-id (first arguments)]
                              (printer/cprint
                               (invoke-route :getMilestone {:milestone-public-id milestone-id}))))}

                   {:command "create"
                    :description "Create a new Milestone"
                    :runs print-args}

                   {:command "update"
                    :description "Update a Milestone"
                    :runs print-args}

                   {:command "delete"
                    :description "Delete a Milestone"
                    :runs (fn [{arguments :_arguments}]
                            (let [milestone-id (first arguments)]
                              (printer/cprint
                               (invoke-route :deleteMilestone {:milestone-public-id milestone-id}))))}

                   {:command "epics"
                    :description "List Epics in a Milestone"
                    :runs (fn [{arguments :_arguments}]
                            (let [milestone-id (first arguments)]
                              (printer/cprint
                               (invoke-route :listMilestoneEpics {:milestone-public-id milestone-id}))))}]}

    {:command "project"
     :description "Manage shortcut projects"
     :subcommands [{:command "list"
                    :description "List all Projects"
                    :runs (fn [_]
                            (printer/cprint
                             (invoke-route :listProjects {})))}

                   {:command "view"
                    :description "View a Project by ID"
                    :runs (fn [{arguments :_arguments}]
                            (let [project-id (first arguments)]
                              (printer/cprint
                               (invoke-route :getProject {:project-public-id project-id}))))}

                   {:command "create"
                    :description "Create a new Project"
                    :runs print-args}

                   {:command "update"
                    :description "Update a Project"
                    :runs print-args}

                   {:command "delete"
                    :description "Delete a Project"
                    :runs (fn [{arguments :_arguments}]
                            (let [project-id (first arguments)]
                              (printer/cprint
                               (invoke-route :deleteProject {:project-public-id project-id}))))}

                   {:command "stories"
                    :description "List Stories in a Project"
                    :runs (fn [{arguments :_arguments}]
                            (let [project-id (first arguments)]
                              (printer/cprint
                               (invoke-route :listStories {:project-public-id project-id}))))}]}

    {:command "label"
     :description "Manage shortcut labels"
     :subcommands [{:command "list"
                    :description "List all Labels"
                    :runs (fn [_]
                            (printer/cprint
                             (invoke-route :listLabels {})))}

                   {:command "view"
                    :description "View a Label by ID"
                    :runs (fn [{arguments :_arguments}]
                            (let [label-id (first arguments)]
                              (printer/cprint
                               (invoke-route :getLabel {:label-public-id label-id}))))}

                   {:command "create"
                    :description "Create a new Label"
                    :runs print-args}

                   {:command "update"
                    :description "Update a Label"
                    :runs print-args}

                   {:command "delete"
                    :description "Delete a Label"
                    :runs (fn [{arguments :_arguments}]
                            (let [label-id (first arguments)]
                              (printer/cprint
                               (invoke-route :deleteLabel {:label-public-id label-id}))))}

                   {:command "stories"
                    :description "List Stories with this Label"
                    :runs (fn [{arguments :_arguments}]
                            (let [label-id (first arguments)]
                              (printer/cprint
                               (invoke-route :listLabelStories {:label-public-id label-id}))))}

                   {:command "epics"
                    :description "List Epics with this Label"
                    :runs (fn [{arguments :_arguments}]
                            (let [label-id (first arguments)]
                              (printer/cprint
                               (invoke-route :listLabelEpics {:label-public-id label-id}))))}]}

    {:command "workflow"
     :description "Manage shortcut workflows"
     :subcommands [{:command "list"
                    :description "List all Workflows"
                    :runs (fn [_]
                            (printer/cprint
                             (invoke-route :listWorkflows {})))}

                   {:command "view"
                    :description "View a Workflow by ID"
                    :runs (fn [{arguments :_arguments}]
                            (let [workflow-id (first arguments)]
                              (printer/cprint
                               (invoke-route :getWorkflow {:workflow-public-id workflow-id}))))}]}

    {:command "member"
     :description "Manage shortcut members"
     :subcommands [{:command "list"
                    :description "List all Members"
                    :runs (fn [_]
                            (printer/cprint
                             (invoke-route :listMembers {})))}

                   {:command "view"
                    :description "View a Member by ID"
                    :runs (fn [{arguments :_arguments}]
                            (let [member-id (first arguments)]
                              (printer/cprint
                               (invoke-route :getMember {:member-public-id member-id}))))}

                   {:command "me"
                    :description "Get current authenticated member"
                    :runs (fn [_]
                            (printer/cprint
                             (invoke-route :getCurrentMemberInfo {})))}]}]})

(defn -main [& args]
  ;; Initialize logging with global context
  (μ/set-global-context!
   {:app-name "shortcut-cli"
    :version "0.0.1"
    :env (or (System/getenv "ENV") "production")})

  ;; Start console publisher for logging
  ;; Set MULOG_ENABLED=true environment variable to see logs
  (def publisher
    (when (= "true" (System/getenv "MULOG_ENABLED"))
      (μ/start-publisher! {:type :console :pretty? true})))

  ;; Log application startup
  (μ/log ::application-started :args (vec args))

  ;; Run CLI
  (try
    (cli/run-cmd args config)
    (catch Exception e
      (μ/log ::application-error
             :error (.getMessage e)
             :exception e)
      (throw e))
    (finally
      ;; Give logger time to flush and stop publisher
      (when publisher
        (Thread/sleep 100)
        (publisher)))))
