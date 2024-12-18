#!/bin/bash

# Define the number of processes to spawn
NUM_PROCESSES=3  # Adjust this if you have more processes

# Define paths to configuration files and logs for Lattice Agreement
HOSTS_FILE="../example/hosts"
LOGS_DIR="../example/output"

# Create the logs directory if it doesn't exist
mkdir -p "$LOGS_DIR"

# Array to store the process IDs of each launched process
PIDS=()

# Function to gracefully stop all processes
cleanup() {
    echo "Stopping all processes..."
    for pid in "${PIDS[@]}"; do
        kill -SIGTERM "$pid"  # Send SIGTERM to allow graceful shutdown
    done
    wait  # Wait for all processes to exit
    echo "All processes stopped."
}

# Trap SIGINT and SIGTERM signals to call the cleanup function
trap cleanup SIGINT SIGTERM

# Start each process in the background and store its PID
for ((i=1; i<=NUM_PROCESSES; i++))
do
    CONFIG_FILE="../example/configs/lattice-agreement-$i.config"
    ./run.sh --id "$i" --hosts "$HOSTS_FILE" --output "$LOGS_DIR/$i.output" "$CONFIG_FILE" &
    PIDS+=($!)  # Store the process ID
    echo "Started process $i with PID ${PIDS[-1]}"
done

# Wait for all background processes to complete (or for the script to be terminated)
wait
