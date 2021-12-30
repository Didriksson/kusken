(ns travcrawl.core
  (:gen-class)
  (:require [ring.adapter.jetty :as jetty]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [defroutes GET]]
            [hiccup.page :as page]
            [travcrawl.travhandler :refer [preFetchedAvdelningar, calculateForAvdelningar]]
            ))



(defn avdelningsAccordian [avdelningar]
  (println avdelningar)
  [:div#accordionAvdelningar {:class "accordion"}
   (for [avd avdelningar]
     [:div {:class "card"}
      [:div {:class "card-header" :id (str "heading" (:avdelning avd))}
       [:h5 {:class "mb-0"}
        [:button {:class "btn btn-primary" :type "button" :data-bs-toggle "collapse" :data-bs-target (str "#collapse" (:avdelning avd)) :aria-expanded "true" :aria-controls (str "#collapse" (:avdelning avd))} (:avdelning avd)]]]
      [:div {:id (str "collapse" (:avdelning avd)) :class "collapse" :aria-labelledby (str "heading" (:avdelning avd)) :data-bs-parent "#accordionAvdelningar"}
       [:div {:class "card-body"}
        [:table {:class "table"}
         [:thead
          [:tr {:class "align-middle"}
           [:th {:scope "col"} "Startnummer"]
           [:th {:scope "col"} "Häst"]
           [:th {:scope "col"} "Segerprocent (häst)"]
           [:th {:scope "col"} "Startpoäng"]
           [:th {:scope "col"} "Kusk"]
           [:th {:scope "col"} "Segerprocent (kusk)"]
           [:th {:scope "col"} "Prispengar (kusk)"]
           [:th {:scope "col"} "Svenska folket (%)"]
           [:th {:scope "col"} "Supersnittet"]
           ]]
         (for [hast (:predictions avd)]
           [:tbody
            [:th {:scope "row"} (:startnummer hast)]
            [:td (:hast hast)]
            [:td (get-in hast [:data :segerprocentHast])]
            [:td (get-in hast [:data :startpoang])]
            [:td (:kusk hast)]
            [:td (get-in hast [:data :segerprocentKusk])]
            [:td (get-in hast [:data :prispengarKusk])]
            [:td (get-in hast [:speladprocent])]
            [:td (get-in hast [:supersnitt])]
            ])]]]])])

  (defn index []
  (page/html5 {:ng-app "myApp" :lang "en"}
              [:head
               [:meta {:charset "utf-8"}]
               [:meta {:name "viewport"
                       :content "width=device-width, initial-scale=1"}]
               [:title "Kusken - din vän i sulkyn"]
               (page/include-css "https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css")
               (page/include-js "https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/js/bootstrap.bundle.min.js")
               [:body
                [:div {:class "container"} (avdelningsAccordian (calculateForAvdelningar (preFetchedAvdelningar)))]]]))

(defroutes app-routes
  (GET "/" [] (index))
  (route/not-found "Not Found"))

(defn -main  []
  (jetty/run-jetty (handler/site app-routes)
                   {:port (Integer. (or (System/getenv "PORT") "3000"))
                    :join? false}))

