import { backendApiUrl, requestJson } from "../api/adminApi";
import type { AuthTestConfig, SocialLoginResponse, SocialProvider } from "../types";

const KAKAO_SDK_URL = "https://developers.kakao.com/sdk/js/kakao.min.js";
const GOOGLE_SDK_URL = "https://accounts.google.com/gsi/client";
const NAVER_STATE_KEY = "busan-eumgil-ADMIN:naver-state";
const envAuthConfig: AuthTestConfig = {
  kakaoJavaScriptKey: (import.meta.env.VITE_ADMIN_KAKAO_JAVASCRIPT_KEY as string | undefined)
    || (import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined)
    || "",
  naverClientId: (import.meta.env.VITE_ADMIN_NAVER_CLIENT_ID as string | undefined) || "",
  googleClientId: (import.meta.env.VITE_ADMIN_GOOGLE_CLIENT_ID as string | undefined) || "",
};

declare global {
  interface Window {
    Kakao?: {
      isInitialized: () => boolean;
      init: (key: string) => void;
      Auth: {
        login: (options: {
          success: (auth: { access_token: string }) => void;
          fail: (error: unknown) => void;
        }) => void;
      };
    };
    google?: {
      accounts: {
        oauth2: {
          initTokenClient: (options: {
            client_id: string;
            scope: string;
            callback: (response: { access_token?: string; error?: string }) => void;
          }) => {
            requestAccessToken: (options?: { prompt?: string }) => void;
          };
        };
      };
    };
  }
}

export function currentAdminPageUrl() {
  return `${window.location.origin}${window.location.pathname}`;
}

export async function fetchAuthTestConfig() {
  if (hasAnyAuthConfigValue(envAuthConfig)) {
    return envAuthConfig;
  }

  const response = await fetch(`${backendApiUrl}/auth/test-config`);
  if (!response.ok) {
    throw new Error("소셜 로그인 설정을 불러오지 못했습니다.");
  }
  return response.json() as Promise<AuthTestConfig>;
}

function hasAnyAuthConfigValue(config: AuthTestConfig) {
  return Boolean(config.kakaoJavaScriptKey || config.naverClientId || config.googleClientId);
}

export async function requestServiceToken(provider: SocialProvider, socialAccessToken: string) {
  const response = await requestJson<SocialLoginResponse>("/auth/social-login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      socialProvider: provider,
      socialAccessToken,
    }),
  });
  if (response.signupRequired || !response.accessToken) {
    throw new Error("관리자 계정으로 가입 완료된 사용자만 접근할 수 있습니다.");
  }
  return response.accessToken;
}

export function startNaverLogin(clientId: string) {
  const state = crypto.randomUUID();
  sessionStorage.setItem(NAVER_STATE_KEY, state);

  const authorizeUrl = new URL("https://nid.naver.com/oauth2.0/authorize");
  authorizeUrl.searchParams.set("response_type", "token");
  authorizeUrl.searchParams.set("client_id", clientId);
  authorizeUrl.searchParams.set("redirect_uri", currentAdminPageUrl());
  authorizeUrl.searchParams.set("state", state);
  window.location.href = authorizeUrl.toString();
}

export function consumeNaverCallback() {
  if (!window.location.hash.includes("access_token=")) {
    return null;
  }

  const params = new URLSearchParams(window.location.hash.slice(1));
  const accessToken = params.get("access_token");
  const state = params.get("state");
  const expectedState = sessionStorage.getItem(NAVER_STATE_KEY);
  sessionStorage.removeItem(NAVER_STATE_KEY);
  history.replaceState(null, document.title, window.location.pathname + window.location.search);

  if (!accessToken) {
    return null;
  }
  if (expectedState && state !== expectedState) {
    throw new Error("네이버 로그인 state가 일치하지 않습니다.");
  }
  return accessToken;
}

export async function getKakaoAccessToken(javaScriptKey: string) {
  await loadScript(KAKAO_SDK_URL);
  const kakao = window.Kakao;
  if (!kakao) {
    throw new Error("카카오 SDK를 불러오지 못했습니다.");
  }
  if (!kakao.isInitialized()) {
    kakao.init(javaScriptKey);
  }

  return new Promise<string>((resolve, reject) => {
    kakao.Auth.login({
      success: (auth) => resolve(auth.access_token),
      fail: (error) => reject(new Error(toErrorMessage(error, "카카오 로그인에 실패했습니다."))),
    });
  });
}

export async function getGoogleAccessToken(clientId: string) {
  await loadScript(GOOGLE_SDK_URL);
  const google = window.google;
  if (!google) {
    throw new Error("구글 SDK를 불러오지 못했습니다.");
  }

  return new Promise<string>((resolve, reject) => {
    const tokenClient = google.accounts.oauth2.initTokenClient({
      client_id: clientId,
      scope: "openid email profile",
      callback: (response) => {
        if (response.error || !response.access_token) {
          reject(new Error(response.error || "구글 로그인에 실패했습니다."));
          return;
        }
        resolve(response.access_token);
      },
    });
    tokenClient.requestAccessToken({ prompt: "consent" });
  });
}

function loadScript(src: string) {
  return new Promise<void>((resolve, reject) => {
    const existing = document.querySelector(`script[src="${src}"]`);
    if (existing) {
      resolve();
      return;
    }
    const script = document.createElement("script");
    script.src = src;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`${src} 로딩에 실패했습니다.`));
    document.head.appendChild(script);
  });
}

function toErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error) {
    return error.message;
  }
  if (typeof error === "string") {
    return error;
  }
  return fallback;
}

export function authConfigGuide() {
  return `제공자 콘솔 등록값: origin ${window.location.origin}, redirect URI ${currentAdminPageUrl()}, backend ${backendApiUrl}`;
}
