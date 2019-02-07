#! /bin/bash -u


for i in `ls |grep ^work`
do 
	 #wf=$(echo $i | cut -d '-' -f 2)
	wf=`echo $i | cut -d '-' -f 2`
	cd $i
	../launcher_generator_2.sh $i yes
	cd ..

done
