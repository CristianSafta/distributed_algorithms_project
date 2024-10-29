#!/bin/bash

# Define the number of processes to spawn
NUM_PROCESSES=3

# Define paths to configuration files and logs
HOSTS_FILE="../example/hosts"
CONFIG_FILE="../example/configs/perfect-links.config"
LOGS_DIR="../example/output"

# Array to store the process IDs of each launched process
PIDS=()

# Function to gracefully stop all processes
cleanup() {
    echo "Stopping all processes..."
    for pid in "${PIDS[@]}"; do
        kill -SIGINT "$pid"  # Send SIGINT instead of SIGTERM
    done
    wait  # Wait for all processes to exit
    echo "All processes stopped."
}

# Trap SIGINT and SIGTERM signals to call the cleanup function
trap cleanup SIGINT SIGTERM

# Start each process in the background and store its PID
for ((i=1; i<=NUM_PROCESSES; i++))
do
    ./run.sh --id "$i" --hosts "$HOSTS_FILE" --output "$LOGS_DIR/$i.output" "$CONFIG_FILE" &
    PIDS+=($!)  # Store the process ID
    echo "Started process $i with PID ${PIDS[-1]}"
done

# Wait for all background processes to complete (or for the script to be terminated)
wait
