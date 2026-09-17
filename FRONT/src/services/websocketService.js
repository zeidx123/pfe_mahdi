import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WEBSOCKET_URL = 'http://localhost:8080/ws';

class WebSocketService {
  constructor() {
    this.stompClient = null;
    this.connected = false;
    this.subscribers = [];
  }

  connect(onMessageReceived) {
    if (this.connected) return;

    // Use SockJS fallback
    const socket = new SockJS(WEBSOCKET_URL);
    
    this.stompClient = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.stompClient.onConnect = (frame) => {
      console.log('Connected to WebSocket:', frame);
      this.connected = true;

      // Subscribe to the topic
      this.stompClient.subscribe('/topic/positions', (message) => {
        if (message.body) {
          const position = JSON.parse(message.body);
          onMessageReceived(position);
        }
      });
    };

    this.stompClient.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    this.stompClient.onWebSocketClose = () => {
      console.log('WebSocket connection closed');
      this.connected = false;
    };

    this.stompClient.activate();
  }

  disconnect() {
    if (this.stompClient !== null) {
      this.stompClient.deactivate();
    }
    this.connected = false;
    console.log('Disconnected from WebSocket');
  }
}

export const websocketService = new WebSocketService();
