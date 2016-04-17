(ns hsbox.launch
  (:require [hsbox.stats :as stats])
  (:require [hsbox.util :refer [file-exists?]])
  (:require [hsbox.db :as db])
  (:require [hsbox.version :refer [os-name]])
  (:require [clojure.java.io :as io]))

(taoensso.timbre/refer-timbre)

(def HEADSHOTBOX-WATERMARK "// Generated by Headshot Box")

(defn generated-by-hsbox [vdm-path]
  (.startsWith (slurp vdm-path) HEADSHOTBOX-WATERMARK))

(defn vdm-watch [demo steamid tick & [tick-end]]
  (let [user-id (get (:player_slots demo) steamid 0)
        cfg (:vdm_cfg (db/get-config))
        commands []
        append-maybe (fn [x pred xs] (if pred (conj x xs) x))]
    (-> commands
        (append-maybe (:player_slots demo)
                      {:factory  "PlayCommands"
                       :tick     (or tick 0)
                       :commands (str "spec_player " (inc user-id))})
        (append-maybe (not (empty? cfg))
                      {:factory  "PlayCommands"
                       :tick     (or tick 0)
                       :commands (str "exec " cfg)})
        (append-maybe (and tick-end (:vdm_quit_after_playback (db/get-config)))
                      {:factory  "PlayCommands"
                       :tick     tick-end
                       :commands "quit"}))))

(defn generate-command [number command]
  (let [line (fn [key value] (str "\t\t" key " \"" value "\"\n"))]
    (str "\t\"" number "\"\n"
         "\t{\n"
         (line "factory" (:factory command))
         (line "name" ".")
         (line "starttick" (:tick command))
         (when (= (:factory command) "PlayCommands")
           (line "commands" (:commands command)))
         "\t}\n")))

(defn generate-vdm [commands]
  (str HEADSHOTBOX-WATERMARK
       "\ndemoactions\n{\n"
       (apply str
              (mapv #(generate-command (first %) (second %))
                    (map vector (rest (range)) commands)))
       "}\n"))

(defn delete-vdm [vdm-path]
  (debug "Deleting vdm file" vdm-path)
  (io/delete-file vdm-path true))

(defn watch [local? demoid steamid round-number tick highlight]
  (let [demo (get stats/demos demoid)
        demo-path (db/demo-path demoid)
        vdm-path (str (subs demo-path 0 (- (count demo-path) 4)) ".vdm")
        play-path (if local? (db/demo-path demoid) (str "replays/" demoid))]
    (if (nil? demo)
      ""
      (do
        (when round-number
          (assert (<= 1 round-number (count (:rounds demo)))))
        (let [round (when round-number (nth (:rounds demo) (dec round-number)))
              tick (if (not (nil? round))
                     (+ (:tick round)
                        (stats/seconds-to-ticks 15 (:tickrate demo)))
                     tick)]
          ; VDM works only with local requests
          (when local?
            (when
              (and (not (:vdm_enabled (db/get-config)))
                   (file-exists? vdm-path)
                   (generated-by-hsbox vdm-path))
              (delete-vdm vdm-path))
            (when (and
                    (:vdm_enabled (db/get-config))
                    (file-exists? demo-path)
                    ; Don't rewrite the .vdm if not created by headshot box
                    (or (not (file-exists? vdm-path))
                        (generated-by-hsbox vdm-path)))
              (if (and (#{"high" "low"} highlight))
                (when (file-exists? vdm-path)
                  (delete-vdm vdm-path))
                (do
                  (debug "Writing vdm file" vdm-path)
                  (spit vdm-path (generate-vdm (vdm-watch demo steamid tick
                                                          (when round (+ (:tick_end round)
                                                                         (stats/seconds-to-ticks 5 (:tickrate demo))))))))))
            (when (and (:playdemo_kill_csgo (db/get-config)))
              (if (= os-name "windows")
               (clojure.java.shell/sh "taskkill" "/im" "csgo.exe" "/F")
             (clojure.java.shell/sh "killall" "-9" "csgo_linux"))))
          {:url (str "steam://rungame/730/" steamid "/+playdemo \"" play-path
                     (when tick (str "@" tick)) "\" "
                     (when highlight steamid)
                     (when (= highlight "low") " lowlights"))})))))

(defn delete-generated-files []
  (let [path (db/get-demo-directory)]
    (->> (clojure.java.io/as-file path)
         file-seq
         (map #(when (and (.endsWith (.getName %) ".vdm") (generated-by-hsbox %))
                (delete-vdm (.getAbsolutePath %))))
         dorun)))
