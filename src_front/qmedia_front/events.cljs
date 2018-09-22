(ns qmedia-front.events
  (:require-macros [qmedia-front.macros :refer [env]])
  (:require [qmedia-front.db :as db]
            [ajax.core :refer [GET POST]]
            [reagent.debug :refer [log error]]
            [debux.cs.core :as d :refer-macros [clog clogn dbg dbgn break]]
            [qmedia-front.fs :as fs]
            [clojure.string :as str]
            [cljs.nodejs :as nodejs]
            [re-frame.core
             :refer [reg-fx
                     dispatch
                     reg-event-db
                     reg-sub
                     reg-event-fx
                     reg-cofx]
             :as rf]))

(def ptn (nodejs/require "parse-torrent-name"))

(def search-url "https://api.themoviedb.org/3/search/movie")

(reg-fx ::media fs/effect)

(defn get-best-match
  [])

(reg-event-db
 ::store-movie
 (fn [db [_ title query-result]]
   (let [results (:results query-result)]
     (let [m (if (= (:total_results query-result) 1)
               (first results)
               (last (sort-by :vote_count results)))]
       (assoc-in db [:media title :meta] m)))))

(reg-fx
 ::search-movie
 (fn [{:keys [title year]
       :or {year false}}]
   (GET search-url {:params {:api_key (env :api-key)
                             :query title
                             :year year}
                    :response-format :json
                    :keywords? true
                    :handler #(rf/dispatch [::store-movie title %])})))

(reg-event-db
 ::set-media
 (fn [db [_ files]]
   (let [media (->>
                (map (fn [file]
                       (let [m (-> (ptn (:basename file))
                                   (js->clj :keywordize-keys true))]
                         (into file m)))
                     files)
                doall
                (group-by :title)
                (map (fn [[k v]]
                       (if (= (count v) 1)
                         {k {:fs (first v)
                             :movie? true}}
                         {k {:fs v
                             :movie? false}})))
                (into {}))]
     (assoc db :media media))))

(reg-event-db
 ::set-error
 (fn [db [_ e]]
   (error e)
   (assoc db :error e)))

;; add handler ::fetch-files
;; try to fetch from local storage
;; else fire files fx

(reg-event-fx
 :initialize-db
 (fn []
   {:db db/default-db
    ::media {:dir (:root-dir db/default-db)
             :on-success [::set-media]
             :on-failure [::set-error]}}))

(reg-event-fx
 :set-active-title
 (fn [{:keys [db]} [_ title data]]
   (let [m {:db (assoc db :active-title title)}]
     (cond
       (:movie? data)
       (assoc m ::search-movie {:title title
                                :year (-> data :fs :year)})
       :else m)))
 )
