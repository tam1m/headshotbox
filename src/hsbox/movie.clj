(ns hsbox.movie
  (:require [clojure.stacktrace :refer [print-cause-trace]]
            [taoensso.timbre :as timbre]
            [hsbox.launch :refer [kill-csgo-process write-vdm-file raw-data-folder escape-path]]
            [hsbox.demo :refer [get-demo-info]]
            [hsbox.stats :as stats]
            [clojure.string :as str])
  (:import (hsbox.java SysTrayIcon)
           (java.util.concurrent.locks ReentrantLock)))

(timbre/refer-timbre)

(def resolution [1280 720])
(def ffmpeg-path "D:\\usr\\ffmpeg-3.4-win64-static\\bin\\ffmpeg.exe")
(def output-folder "e:\\tmp\\movie\\")

(defn record-round [demoid steamid round]
  (try
    ;(SysTrayIcon/openWebpage url)
    (kill-csgo-process)
    (let [demo (get stats/demos demoid)
          x (assert (:path demo))
          path (clojure.string/replace (:path demo) "\\" "/")
          ;demo (assoc (get-demo-info path) :path path)
          vdm-info (write-vdm-file demo steamid 0 round "round")
          x (println (:clip-ids vdm-info))]
      (future
        (clojure.java.shell/sh "D:\\usr\\hlae\\HLAE.exe" "-csgoLauncher" "-noGui" "-autoStart"
                               "-gfxEnabled" "true" "-gfxWidth" (str (first resolution)) "-gfxHeight" (str (second resolution)) "-gfxFull" "true"
                               "-customLaunchOptions" (str "\"-windowed -novid +playdemo " path "@" (:tick vdm-info) "\""))
        (doall (map #(apply clojure.java.shell/sh (concat [ffmpeg-path]
                                                          (str/split "-y -f image2 -framerate 60" #" ")
                                                          ["-i" (escape-path (str raw-data-folder % "%04d.tga"))
                                                           "-i" (escape-path (str raw-data-folder % ".wav"))]
                                                          (str/split "-vcodec libx264 -qp 23 -r 60 -acodec libmp3lame -b:a 256K" #" ")
                                                          [(escape-path (str output-folder % ".mp4"))]))
                    (:clip-ids vdm-info))))
      )
    (catch Throwable e (do
                         (print-cause-trace e)
                         (error e)))))

(defn make-movie [steamid plays filters]
  (let [big-plays (stats/get-big-plays steamid plays filters)]
    (doall (map #(record-round (:demoid %) steamid (:round-number %)) big-plays))
    (clojure.java.shell/sh "e:/tmp/movie/make.bat")))