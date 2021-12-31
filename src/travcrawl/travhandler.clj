(ns travcrawl.travhandler
  (:gen-class)  
  (:require [clj-http.client :as client]
            [clojure.string :as str]))

(require '[reaver :refer [parse extract extract-from attr text jsoup]])

(def startlistapage (slurp "https://spelatrav.se/v75"))

(defn hamtaStatistikInformationForHast [id]
  (->>
   (client/get (str "https://api.travsport.se/webapi/horses/statistics/organisation/TROT/sourceofdata/SPORT/horseid/" id) {:as :json})
   (:body)))

(defn hamtaStatistikInformationForKusk [id]
  (->>
   (client/get (str "https://api.travsport.se/webapi/licenseholder/drivers/statistics/organisation/TROT/sourceofdata/SPORT/driverid/" id) {:as :json})
   (:body)))


(defn hamtaStatistikForHast [id]
  (let [statistik (hamtaStatistikInformationForHast id)]
    {:segerprocent (read-string (str/replace (:winningRate statistik) #"\s" ""))
     :startpoang (read-string (str/replace (:points statistik) #"\s" ""))}))

(defn hamtaStatikForKusk [id]
  (let [statistik (hamtaStatistikInformationForKusk id)]
    statistik))


(defn parseEnHast [hast]
  (let [haststatistik (extract hast [:namn :statistikurl]
                               "td" text
                               "a" (attr :href))
        hastMedId (merge haststatistik {:id (second (re-find #"visa\/(.+)\/resultat" (:statistikurl haststatistik)))})]
    (merge hastMedId (hamtaStatistikForHast (:id hastMedId)))))

(defn parseEnKusk [kusk]
  (let [kuskurl (extract kusk [:namn :statistikurl]
                         "td" text
                         "a" (attr :href))
        kuskMedId (merge kuskurl {:id (second (re-find #"visa\/(.+)\/kuskstat" (:statistikurl kuskurl)))})]
    (merge kuskMedId (hamtaStatikForKusk (:id kuskMedId)))))

(defn parseEkipage [avdelning]
  (->> (extract-from avdelning
                     "tr" [:startnummer :hast :kusk :speladprocent]
                     "td:nth-child(1)" text
                     "td:nth-child(2)" jsoup
                     "td:nth-child(3)" jsoup
                     "td:nth-child(9)" text)
       (map #(update-in % [:hast] parseEnHast))
       (map #(update-in % [:kusk] parseEnKusk))))

(defn parseStartplats [avdelning]
  (update-in avdelning [:hastar] parseEkipage))

(defn parseAvdelning [site]
  (extract-from
   site ".row > div"
   [:avd :hastar]
   "h4" text
   "tbody > tr" jsoup))


(defn snittHastar [hastar attSnitta]
  (float (/ (reduce + (map #(get-in % attSnitta) hastar)) (count hastar))))
;; => #'travcrawl.core/snittHastar



(defn snittAvdelning [avdelning]
  (let [hastar (:hastar avdelning)]
    {:snittSegerprocentHast (snittHastar hastar [:berakning :segerprocentHast])
     :snittStartpoang (snittHastar hastar [:berakning :startpoang])
     :snittSegerprocentKusk (snittHastar hastar [:berakning :segerprocentKusk])
     :snittPrispengarKusk (snittHastar hastar [:berakning :prispengarKusk])}))

(defn beraknaSnittForHast [snittForAvdelning hast]
  (let [snitt  {:jmfSegerprocentHast (/ (get-in hast [:berakning :segerprocentHast] hast) (:snittSegerprocentHast snittForAvdelning))
                :jmfStartpoang (/ (get-in hast [:berakning :startpoang] hast) (:snittStartpoang snittForAvdelning))
                :jmfSegerprocentKusk (/ (get-in hast [:berakning :segerprocentKusk] hast) (:snittSegerprocentKusk snittForAvdelning))
                :jmfPrispengarKusk (/ (get-in hast [:berakning :prispengarKusk] hast) (:snittPrispengarKusk snittForAvdelning))}]

    (assoc snitt
           :supersnitt (/ (reduce + (vals snitt)) (count (vals snitt))))))



(defn getDataForEkipage [ekipage]
  {:segerprocentHast (get-in ekipage [:hast :segerprocent])
   :startpoang (get-in ekipage [:hast :startpoang])
   :segerprocentKusk (:winningRates (first (get-in ekipage [:kusk :statistics])))
   :prispengarKusk (:prizeMoney (first (get-in ekipage [:kusk :statistics])))})


(defn calculateForAvdelning [avdelning]
  (assoc avdelning :hastar (map #(assoc %
                                        :berakning (getDataForEkipage %)) (:hastar avdelning))))

(defn beraknaSnitten [avdelning]
  (let [snittForAvdelning (snittAvdelning avdelning)]
    (assoc avdelning :hastar (map #(assoc % :berakning (beraknaSnittForHast snittForAvdelning %)) (:hastar avdelning)))))

(defn sort-by-supersnitt [avdelning]
  (assoc avdelning :hastar (reverse (sort-by #(get-in % [:berakning :supersnitt]) (:hastar avdelning)))))

(defn display-results [avdelning]
  {:avdelning (:avd avdelning)
   :predictions (map #(do
                        {:startnummer (:startnummer %)
                         :hast (get-in % [:hast :namn])
                         :kusk (get-in % [:kusk :namn])
                         :data (getDataForEkipage %)
                         :speladprocent (:speladprocent %)
                         :supersnitt (format "%.3f" (get-in % [:berakning :supersnitt]))})
                     (:hastar avdelning))})


(defn narrow-down [avdelning]
  {:avdelning (:avdelning avdelning)
   :hastar (take 2 (:predictions avdelning))})


(defn fetchAndSave []
  (spit "data.txt" (->>
                    (parse startlistapage)
                    (parseAvdelning)
                    (map parseStartplats)
                    (prn-str))))


(defn calculateForAvdelningar [avdelningar]
  (->>
   (map calculateForAvdelning avdelningar)
   (map beraknaSnitten)
   (map display-results))
  )

(defn fetchAvdelning []
  (->>
   (parse startlistapage)
   (parseAvdelning)
   (map parseStartplats)))

(defn preFetchedAvdelningar []
  (->>
   (read-string (slurp "data.txt"))))
     

(defn preFetchedCalculate []
  (->>
   (read-string (slurp "data.txt"))
   (map calculateForAvdelning)
   (map beraknaSnitten)
   (map sort-by-supersnitt)
   (map display-results)
   (map narrow-down)))

(defn fetchAndCalculate []
  (->>
   (parse startlistapage)
   (parseAvdelning)
   (map parseStartplats)
   (map calculateForAvdelning)
   (map beraknaSnitten)
   (map sort-by-supersnitt)
   (map display-results)
   (map narrow-down)))