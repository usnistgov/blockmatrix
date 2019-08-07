cd C:\Users\tns13\IdeaProjects\timestampingauthority.TimeStampingAuthority\test
set path=C:\Program Files\Java\jdk-12.0.1\bin;%path%
start cmd /k java -jar TimeStampingAuthorityServer.jar
timeout 15
start cmd /c java -jar timestampingauthority.Client.jar -a localhost -p 124 -r 50 -n NIST-1 -c 200
start cmd /c java -jar timestampingauthority.Client.jar -a localhost -p 124 -r 50 -n NIST-2 -c 200
start cmd /c java -jar timestampingauthority.Client.jar -a localhost -p 124 -r 50 -n NIST-3 -c 200
timeout 15
start cmd /k java -jar timestampingauthority.OrderChecker.jar --prefix NIST
exit