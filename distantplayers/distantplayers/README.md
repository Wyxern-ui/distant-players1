# Distant Players (Fabric 1.21.11)

Server raycasts between players; clients draw a scaled-down copy of any player with a clear line of
sight, parked at the edge of the client's render range along the same line of sight.

Build:  ./gradlew build     ->  build/libs/distantplayers-1.0.0.jar
Install the jar on the server AND on every client that should see distant players (needs Fabric API).
Tunables: DistantPlayers.java (MIN_DISTANCE, MAX_DISTANCE, INTERVAL_TICKS).
