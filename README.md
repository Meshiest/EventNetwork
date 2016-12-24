Everything is javadoc'd! 

## Client Example

    new Client("localhost", 4333, new EventClient() {
      public void init() {
        on(this, "pong", "pingCallback");
      }
      public void pingCallback(int counter) {
        System.out.println("Got pinged: " + counter + " times");
        emit("ping", counter + 1);
      }
      public void onConnect() {
        emit("ping", 0);
      }
      public void onConnectFail() {}
      public void onDisconnect() {}
      public void onRawMessage(String message) {}
    });

## Server Example

    new Server(4333, new EventServer() {
      public void init() {
        on(this, "ping", "pongCallback");
      }
      public void pongCallback(int clientId, int counter) {
        emit(clientId, "pong", counter);
      }
      public void onClientConnect(int clientId) {}
      public void onClientDisconnect(int clientId) {}
      public void onRawMessage(int clientId, String message) {}
    });

To hide the server gui, simply run `server.setVisible(false);`