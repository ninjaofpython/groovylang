import com.att.nccicd.config.conf as config
conf = new config(env).CONF

currentBuild.displayName = "#${BUILD_NUMBER} ${PUSH_PATH}"
if (params.ARTIFACT == '' || params.PUSH_PATH == ''  || params.CHECKSUM == '') {
  error ("Please provide correct parameters")
}
clamav_fail_count = 0
clamav_fail_objects = ""
def clamav_scan (it) { // clamav scan
    scanType = "open_file"
    def clamav_check = false
    if (it != null) {
        fileName=it.substring(it.lastIndexOf("/")+1)
        reportDirectory = "${fileName}-${BUILD_TIMESTAMP}"
                str_ReportDirectory = "${fileName}-${BUILD_TIMESTAMP}"
    }
    sh """
        export reportDirectory=$str_ReportDirectory
        export tempDirectory="clamavtempdir"
        export open_file="${it}"
        export needCleanup="True"
        export scanType=${scanType}
        python3 ../nc-cicd/tools/clambake/containerscan.py
    """

    dir("${WORKSPACE}/jenkins/${reportDirectory}") {
        sh(returnStatus: true, script: """ls *.log > logFile.txt """)
        def filePath = readFile "logFile.txt"
        def file_names = filePath.readLines()
        for (file_name in file_names) {
            sh """
                set +e
                cat ${file_name}
                tail -15 ${file_name} | grep 'Infected files' >> results.txt \
                && tail -15 ${file_name} | grep 'Scanned files' >> results.txt \
                && echo "**************************************************" >> results.txt
            """
        }
        sh(returnStatus: true, script: """cat results.txt | grep 'Infected files:' | sed 's/^.*: //' > infected.txt """)
        infected = readFile "infected.txt"
        infected_count = infected.readLines()
        for (count in infected_count) {
          if (count != "0") {
            clamav_check = true
          }
        }
        println(clamav_check)
        sh "rm *.txt"
    }
    if (clamav_check.toBoolean()) {
      current_obj_status = 1
      clamav_fail_count = clamav_fail_count + 1
      clamav_fail_objects += "${clamav_fail_count} - ${current_obj} \n"
      print 'Failed objects'
      println clamav_fail_objects
    }
    print 'Pushing clamav scan the logs and result to Artifactory'
    sh(returnStatus: true, script: """ls -ltr ${WORKSPACE}/jenkins/${reportDirectory}/ | awk '{if (\$4) print \$9;}' > artf.txt """)
   artifactory.upload("${reportDirectory}/*.*", "nc-security/clamav-check/${BUILD_NUMBER}-${BUILD_TIMESTAMP}/")
}

def label = "worker-${UUID.randomUUID().toString()}"
try {
podTemplate(label: label, showRawYaml: false,
            yaml: cicd_helper.podExecutorConfig(conf.JNLP_IMAGE, "0", "dind-node", "dind-node"),
            containers: [
                containerTemplate(name: "ubuntu",
                                  image: conf.CLAMAV_PYTHON3,
                                  command: "cat",
                                  ttyEnabled: true,
                                  envVars: [
                                    envVar(key: "http_proxy", value: HTTP_PROXY),
                                    envVar(key: "https_proxy", value: HTTP_PROXY),
                                    envVar(key: "HTTP_PROXY", value: HTTP_PROXY),
                                    envVar(key: "HTTPS_PROXY", value: HTTP_PROXY),
                                    envVar(key: "no_proxy", value: NO_PROXY),
                                    envVar(key: "NO_PROXY", value: NO_PROXY)
                ])],
                volumes: [hostPathVolume(
                              hostPath: '/var/run/dindproxy/docker.sock',
                              mountPath: '/var/run/docker.sock'
                        )]) {
    node(label){
          container("ubuntu"){
                   stage('setup freshclam'){
                sh '''
                    apt-get update -y
                    apt-get install wget curl -y
                    sed -i 's/30/600/g' /etc/clamav/freshclam.conf
                    echo 'PrivateMirror clamav-nc.cci.att.com' >> /etc/clamav/freshclam.conf
                    sed -i 's/ScriptedUpdates yes/ScriptedUpdates no/g' /etc/clamav/freshclam.conf
                    cat /etc/clamav/freshclam.conf
                    freshclam
                    service clamav-freshclam restart
                    service clamav-daemon start
                    service clamav-daemon status
                '''
            }
             stage('Project Checkout: nc-cicd'){
                gerrit.cloneProject("${INTERNAL_GERRIT_SSH}/nc-cicd",
                                     "main",
                                     "refs/heads/*:refs/remotes/origin/*",
                                     "nc-cicd",
                                     "${INTERNAL_GERRIT_KEY}")
            }

              dir("${WORKSPACE}/jenkins"){
                                    def pullFailure = []
                                   ARTIFACT.split(" ").each {
                                       // current_obj = it.substring(it.lastIndexOf("/")+1)
                                        current_obj = it
                                        current_obj_status = 0
                                        int pullStatus = 0
                     withCredentials([usernamePassword(credentialsId: 'jenkins-artifactory',
                        usernameVariable: 'ARTIFACTORY_USER',
                        passwordVariable: 'ARTIFACTORY_PASSWORD')]){
                      stage("Download file and check if already uploaded") {
                            println it
                            sh "wget ${it}"
                            actualFile = it.substring(it.lastIndexOf("/")+1)
                            file_size=sh(script: "ls -lrt ${actualFile} |  awk '{ print \$5 }'", returnStdout: true)
                            file_cksum = sh(script: "cksum ${actualFile}", returnStdout: true)
                            file_md5sum = sh(script: "md5sum ${actualFile}", returnStdout: true)
                            file_sha256sum = sh(script: "sha256sum ${actualFile}", returnStdout: true)
                            file_sha512sum = sh(script: "sha512sum ${actualFile}", returnStdout: true)
                            if (file_cksum.replaceAll("\\s", "").contains(params.CHECKSUM.replaceAll("\\s", "")) || file_md5sum.replaceAll("\\s", "").contains(params.CHECKSUM.replaceAll("\\s", "")) || file_sha256sum.replaceAll("\\s", "").contains(params.CHECKSUM.replaceAll("\\s", "")) || file_sha512sum.replaceAll("\\s", "").contains(params.CHECKSUM.replaceAll("\\s", ""))){
                              println("Checksum match found: " + params.CHECKSUM.trim())
                            }
                            else {
                              println("No checksum match found. ")
                              println("Expected CKSUM: " + file_cksum)
                              println("Expected MD5SUM: " + file_md5sum)
                              println("Expected SHA256: " + file_sha256sum)
                              println("Expected SHA512: " + file_sha512sum)
                              println("Received:  + params.CHECKSUM
                            }
                            pullStatus = sh(script: " curl -s -L -I  ${it} | grep \"Content-Length: ${file_size}\"", returnStatus: true)
                            uploadedFile = "https://artifacts-nc.zc1.cti.att.com/artifactory/${params.PUSH_PATH}/${actualFile}"
                            uploadedFilePresent = sh(script: "curl --output /dev/null --silent --head --fail ${uploadedFile}", returnStatus: true)
               } //end of download stage
               if (pullStatus == 0) {
                   if (uploadedFilePresent != 0) {
                      stage("clamav scan and upload") {
                      clamav_scan(current_obj)
                      print(current_obj_status)
                      if ( current_obj_status == 0 ) {
                          println("Artifact passed clamavscan uploading to artifactory")
                         artifactory.upload(it.substring(it.lastIndexOf("/")+1),"${params.PUSH_PATH}")
                          println("Artifact uploaded to artifactory")
                      }
                    }
                   }
                  else {
                        println("File ${it} is already uploaded at desired path")
                  }
               }
              else {
                            pullFailure.add("${it}")
                   }
                           } // end of withCredentials
                               } // end of loop
                                     if(pullFailure || clamav_fail_count > 0){
                                summary = manager.createSummary('error.gif')
                                summary.appendText('<h3>Error!</h3>')
                                summary.appendText("<p>add_artifacts job failed!</p>")
                                if (pullFailure) {
                                  sh 'echo "\n-------\n List of artifacts which could not be pulled:" >> artifacts-pull-scan-report.txt'
                                  summary.appendText("<p> List of artifacts which could not be pulled:</p>")

                                  for(String item: pullFailure) {
                                    sh 'echo "${item}" >> artifacts-pull-scan-report.txt'
                                    summary.appendText("<li> ${item} </li>")
                                  }
                                }
                                if (clamav_fail_count > 0)
                                 {
                                  println "\n-------\n List of artifacts which failed clamavscan: "
                                  sh 'echo "\n-------\n List of artifacts which failed clamavscan" >> artifacts-pull-scan-report.txt'
                                  summary.appendText("<p>List of artifacts which failed clamavscan:</p>")
                                    println clamav_fail_objects
                                    sh "echo '${clamav_fail_objects}' >> artifacts-pull-scan-report.txt"
                                }
                                println "\n-------\n"
                                sh 'echo "\n-------\n" >> artifacts-pull-scan-report.txt'
                                archiveArtifacts allowEmptyArchive: true, artifacts: "artifacts-pull-scan-report.txt", onlyIfSuccessful: false
                                currentBuild.result = "FAILURE"
                            }
                            else {
                                    println("All artifacts uploaded successfully")
                                    summary = manager.createSummary('success.gif')
                                    summary.appendText("<p> All artifacts uploaded successfully</p>")
                                  }
              } // end of directory
          }//end of container
  } // end of node
} // end of podtemplate
} // end of try
catch (Exception ex) {
    println("Exception Occured: ${ex}")
    currentBuild.result = "FAILURE"
}
