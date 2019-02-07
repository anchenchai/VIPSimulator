#! /bin/bash -u
algo=${1:? need replicas selection algo as parameter, algo can be "lcg_cp" or "dyn" corresponding to the choice in simulator}
for i in `ls |grep ^work`
do 
	cd $i
	for j in `ls simgrid_files/ |grep Lfc`
	do 
		sh run_simulations_${i}.sh ${j} ${algo}

	done	
    cd ..
done
