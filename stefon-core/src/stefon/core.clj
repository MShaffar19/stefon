(ns stefon.core
  (:require [stefon.settings :as settings]
            [stefon.asset :as asset]
            [stefon.path :as path]
            [stefon.digest :as digest]
            [stefon.util]
            [stefon.asset.reference :as ref]
            [stefon.asset.stefon :as stefon]
            [stefon.manifest :as manifest]
            [stefon.precompile :as precompile]
            [stefon.util :refer (dump wrap-dump)]
            [clojure.java.io :as io]
            [ring.util.response :as response]
            [ring.middleware.file :refer (wrap-file)]
            [ring.middleware.file-info :refer (wrap-file-info)]
            [stefon.middleware.expires :refer (wrap-file-expires-never wrap-expires-never)]))

;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Entry points
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn link-to-asset [adrf options]
  "path should start under assets and not contain a leading slash
ex. (link-to-asset \"javascripts/app.js\") => \"/assets/javascripts/app-12345678901234567890123456789012.js\""
  (settings/with-options options
    (if (settings/production?)
      (manifest/fetch adrf)
      (-> adrf asset/find-and-compile-and-save first))))

(defn wrap-undigested-link
  [app production? options]
  (fn [req]
    (if (or production?
            (-> req :uri path/asset-uri? not))
      (app req)
      (try
        (let [adrf (-> req :uri path/uri->adrf)
              link (link-to-asset adrf options)]
          (response/redirect link))
        (catch java.io.FileNotFoundException e
          (app req))))))

(defn asset-pipeline
  "Construct the Stefon asset pipeline depending on the :cache-mode option, eventually
   either loading the data from the cache directory, rendering a new resource and
   returning that, or passing on the request to the previously existing request
   handlers in the pipeline."
  [app options]
  (settings/with-options options
    (-> (settings/serving-asset-root) io/file .mkdirs)
    (-> app
        (wrap-undigested-link (settings/production?) options)
        (wrap-file (settings/serving-root))
        (wrap-file-expires-never (settings/serving-root)))))

(defn precompile [options] ;; lein stefon-precompile uses this name
  (precompile/precompile options))

(defn init [options]
  (settings/with-options options
    (when (settings/production?)
      (precompile/load-precompiled-assets))))
