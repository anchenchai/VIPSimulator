#! /bin/bash -u
# Command lines arguments are:
 # Platform files:  platform_workflow-2RcwCY_x_x_lim.xml
 # Deployment file: deployment_workflow-2RcwCY_2.xml
 # Initial number particles: 25
 # Number of gate jobs: 25
 # SoS time: 300
 # Number of merge jobs: 1
 # CPU merge time: 0
 # Events per second: 0
 # version: 1, 2, or 3

wd="simgrid_files/"
Lfc=${1:? need Lfc as paramter}
catalog=${wd}${Lfc}

algo=${2:? need Algo as paramter}
type=`echo ${Lfc} | cut -d "_" -f 1`

verbose=${2:-""}
 if [[ $verbose == "-v" ]]
 then
 	verbose="--log=root.fmt:[%12.6r]%e(%3i:%10P@%40h)%e%m%n"
 else
 	verbose="--log=java.thres:critical"
 fi

version=2

flag="--cfg=network/crosstraffic:0"
cmd="java -cp ../../bin:../../simgrid.jar \
 VIPSimulator"
params="simgrid_files/deployment_workflow-2RcwCY.xml \
 25 25 300 1 0 0 ${version} 10000000 ${verbose}"


echo "${algo} is used for ${catalog}" 
for i in {1..1}
    do
      for platform in "Avg_Merged_factor" "3level_scale"
      do
        echo -e "\\tSimulate on AS  - version ${version}" 
        platform_file="../Merged_platform_${platform}.xml"
        run=$cmd" ${platform_file} simgrid_files/deployment_workflow-2RcwCY_2.xml 25 25 300 1 0 0 ${version} 10000000 ${catalog} ${algo} 0 ${verbose} ${flag}"
        echo -e "\\t\\t$run"
        $run  1> timings/simulated_time_on_${platform}_${type}_${algo}_${i}.csv \
              2> csv_files/simulated_file_transfer_on_${platform}_${type}_${algo}_${i}.csv
        sed -i 1d csv_files/simulated_file_transfer_on_${platform}_${type}_${algo}_${i}.csv
        sed -i "/bad/d" csv_files/simulated_file_transfer_on_${platform}_${type}_${algo}_${i}.csv      
      done         
    done
