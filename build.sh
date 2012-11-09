rm -rf dist
cd java
mvn package -Pdist
cd ..
mkdir dist
cp java/play/target/greenscript-play-1.2.9.zip dist/greenscript-1.2.9.zip

