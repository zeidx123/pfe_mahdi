import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_URL } from '../utils/constants';

class WebSocketService {
  stompClient = null;
  connected = false;

  connect(onMessageReceived) {
    if (this.stompClient?.active) return;
    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(API_URL.replace(/\/api\/?$/, '') + '/ws'),
      beforeConnect: () => {
        this.stompClient.connectHeaders = { Authorization: `Bearer ${localStorage.getItem('token') || ''}` };
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.connected = true;
        this.stompClient.subscribe('/topic/positions', (message) => {
          if (message.body) onMessageReceived(JSON.parse(message.body));
        });
      },
      onStompError: () => { this.connected = false; },
      onWebSocketClose: () => { this.connected = false; },
    });
    this.stompClient.activate();
  }

  disconnect() {
    this.stompClient?.deactivate();
    this.stompClient = null;
    this.connected = false;
  }
}
export const websocketService = new WebSocketService();
