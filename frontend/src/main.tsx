import React from 'react';
import ReactDOM from 'react-dom/client';
import { GoogleOAuthProvider } from '@react-oauth/google';
import '@fontsource/bungee/latin-400.css';
import '@fontsource/open-sans/latin-400.css';
import '@fontsource/open-sans/latin-600.css';
import '@fontsource/open-sans/latin-700.css';
import './index.css';
import App from './App';
import { AuthProvider, GOOGLE_CLIENT_ID } from './auth/AuthContext';
import { ThemeProvider } from './theme/ThemeProvider';
import { ToastProvider } from './components/ui/Toast';

const tree = (
  <React.StrictMode>
    <ThemeProvider>
      <AuthProvider>
        <ToastProvider>
          <App />
        </ToastProvider>
      </AuthProvider>
    </ThemeProvider>
  </React.StrictMode>
);

ReactDOM.createRoot(document.getElementById('root')!).render(
  GOOGLE_CLIENT_ID ? <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>{tree}</GoogleOAuthProvider> : tree,
);
