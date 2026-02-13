import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import './index.css';
import { Toaster } from 'react-hot-toast';

// Initialize auth state from localStorage
import { useAuthStore } from './stores/auth.store';
useAuthStore.getState().initialize();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
    <Toaster
      position="top-right"
      toastOptions={{
        duration: 4000,
        style: {
          background: 'var(--toast-bg, #fff)',
          color: 'var(--toast-color, #18181b)',
          border: '1px solid var(--toast-border, #e4e4e7)',
          borderRadius: '0.75rem',
          padding: '1rem',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
        },
        success: {
          iconTheme: {
            primary: '#22c55e',
            secondary: '#fff',
          },
        },
        error: {
          iconTheme: {
            primary: '#ef4444',
            secondary: '#fff',
          },
        },
      }}
    />
  </React.StrictMode>
);
