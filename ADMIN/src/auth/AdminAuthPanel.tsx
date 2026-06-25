import { useEffect, useState } from "react";
import {
  authConfigGuide,
  consumeNaverCallback,
  fetchAuthTestConfig,
  getGoogleAccessToken,
  getKakaoAccessToken,
  requestServiceToken,
  startNaverLogin,
} from "./adminSocialAuth";
import {
  backendApiUrl,
  fetchAdminMe,
  getStoredAdminAccessToken,
  normalizeAdminAccessToken,
  storeAdminAccessToken,
} from "../api/adminApi";
import type { AdminMeResponse, AuthTestConfig, SocialProvider } from "../types";

interface AdminAuthPanelProps {
  accessToken: string;
  tokenInput: string;
  onTokenInputChange: (value: string) => void;
  onAccessTokenChange: (value: string) => void;
  onAdminVerified?: (principal: AdminMeResponse | null) => void;
  compact?: boolean;
}

export function AdminAuthPanel({
  accessToken,
  tokenInput,
  onTokenInputChange,
  onAccessTokenChange,
  onAdminVerified,
  compact = false,
}: AdminAuthPanelProps) {
  const [config, setConfig] = useState<AuthTestConfig | null>(null);
  const [principal, setPrincipal] = useState<AdminMeResponse | null>(null);
  const [message, setMessage] = useState("소셜 로그인 또는 accessToken 입력으로 관리자 권한을 확인합니다.");
  const [pendingProvider, setPendingProvider] = useState<SocialProvider | null>(null);

  useEffect(() => {
    fetchAuthTestConfig()
      .then(setConfig)
      .catch((error) => setMessage(error instanceof Error ? error.message : "소셜 로그인 설정을 불러오지 못했습니다."));
  }, []);

  useEffect(() => {
    try {
      const naverAccessToken = consumeNaverCallback();
      if (!naverAccessToken) return;
      void applyProviderToken("NAVER", naverAccessToken);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "네이버 로그인 처리에 실패했습니다.");
    }
  }, []);

  useEffect(() => {
    if (!accessToken) {
      setPrincipal(null);
      onAdminVerified?.(null);
      return;
    }
    fetchAdminMe(accessToken)
      .then((data) => {
        setPrincipal(data);
        onAdminVerified?.(data);
        setMessage(`관리자 확인 완료: ${data.role}`);
      })
      .catch((error) => {
        storeAdminAccessToken("");
        onAccessTokenChange("");
        onTokenInputChange("");
        setPrincipal(null);
        onAdminVerified?.(null);
        setMessage(error instanceof Error ? error.message : "관리자 권한 확인에 실패했습니다.");
      });
  }, [accessToken, onAdminVerified]);

  function saveToken(nextToken = tokenInput) {
    const normalizedToken = normalizeAdminAccessToken(nextToken);
    storeAdminAccessToken(normalizedToken);
    onAccessTokenChange(normalizedToken);
    onTokenInputChange(normalizedToken);
    return normalizedToken;
  }

  function clearToken() {
    storeAdminAccessToken("");
    onAccessTokenChange("");
    onTokenInputChange("");
    onAdminVerified?.(null);
    setPrincipal(null);
    setMessage("토큰을 제거했습니다.");
  }

  async function applyProviderToken(provider: SocialProvider, providerAccessToken: string) {
    setPendingProvider(provider);
    setMessage(`${provider} 로그인 확인 중`);
    try {
      const serviceToken = await requestServiceToken(provider, providerAccessToken);
      const normalizedToken = saveToken(serviceToken);
      const admin = await fetchAdminMe(normalizedToken);
      setPrincipal(admin);
      onAdminVerified?.(admin);
      setMessage(`${provider} 로그인 완료: ${admin.role}`);
    } catch (error) {
      setPrincipal(null);
      onAdminVerified?.(null);
      setMessage(error instanceof Error ? error.message : `${provider} 로그인에 실패했습니다.`);
    } finally {
      setPendingProvider(null);
    }
  }

  async function login(provider: SocialProvider) {
    if (!config) {
      setMessage("소셜 로그인 설정을 아직 불러오지 못했습니다.");
      return;
    }
    try {
      if (provider === "KAKAO") {
        if (!config.kakaoJavaScriptKey) throw new Error("카카오 JavaScript 키가 없습니다.");
        await applyProviderToken("KAKAO", await getKakaoAccessToken(config.kakaoJavaScriptKey));
        return;
      }
      if (provider === "NAVER") {
        if (!config.naverClientId) throw new Error("네이버 Client ID가 없습니다.");
        startNaverLogin(config.naverClientId);
        return;
      }
      if (!config.googleClientId) throw new Error("구글 Client ID가 없습니다.");
      await applyProviderToken("GOOGLE", await getGoogleAccessToken(config.googleClientId));
    } catch (error) {
      setPendingProvider(null);
      setMessage(error instanceof Error ? error.message : `${provider} 로그인 준비에 실패했습니다.`);
    }
  }

  const storedToken = getStoredAdminAccessToken();

  return (
    <section className={compact ? "admin-auth-panel compact" : "admin-auth-panel"}>
      <div className="admin-auth-backend">
        <strong>Backend</strong>
        <span>{backendApiUrl}</span>
      </div>
      <div className="admin-auth-social">
        <button className="login-button kakao" type="button" onClick={() => void login("KAKAO")} disabled={pendingProvider != null}>
          카카오 로그인
        </button>
        <button className="login-button naver" type="button" onClick={() => void login("NAVER")} disabled={pendingProvider != null}>
          네이버 로그인
        </button>
        <button className="login-button google" type="button" onClick={() => void login("GOOGLE")} disabled={pendingProvider != null}>
          구글 로그인
        </button>
      </div>
      {storedToken && (
        <div className="button-row compact">
          <button type="button" onClick={clearToken}>
            저장된 토큰 제거
          </button>
        </div>
      )}
      <div className="admin-auth-status">
        {principal ? <span>{principal.userId} · {principal.role}</span> : <span>{message}</span>}
        {!config && <small>AUTH_TEST_* 설정 또는 /auth/test-config 응답을 확인하세요.</small>}
        {config && <small>{authConfigGuide()}</small>}
      </div>
    </section>
  );
}
