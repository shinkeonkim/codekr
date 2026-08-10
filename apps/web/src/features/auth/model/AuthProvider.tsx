"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { userApi } from "@/entities/user";
import { tokenStore } from "@/shared/api";
import type { TokenResponse, User } from "@/entities/user";

interface AuthState {
  user: User | null;
  /** 첫 세션 복원이 끝나기 전에는 화면이 "비로그인"으로 깜빡이지 않도록 이 값을 본다. */
  loading: boolean;
  isAdmin: boolean;
  signIn: (tokens: TokenResponse) => void;
  signOut: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  // 새로고침 후에도 로그인이 유지되도록, 저장된 토큰으로 내 정보를 한 번 확인한다.
  useEffect(() => {
    let cancelled = false;

    const restore = async () => {
      if (!tokenStore.read()) {
        setLoading(false);
        return;
      }
      try {
        const me = await userApi.me();
        if (!cancelled) setUser(me);
      } catch {
        await refreshOrClear(setUser);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void restore();
    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback((tokens: TokenResponse) => {
    tokenStore.save(tokens);
    setUser(tokens.user);
  }, []);

  const signOut = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  const value = useMemo<AuthState>(
    () => ({ user, loading, isAdmin: user?.role === "ADMIN", signIn, signOut }),
    [user, loading, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth 는 AuthProvider 안에서만 쓸 수 있습니다.");
  return context;
}

/** 액세스 토큰이 만료됐다면 리프레시로 한 번 더 시도하고, 그것도 실패하면 로그아웃한다. */
async function refreshOrClear(setUser: (user: User | null) => void) {
  const refreshToken = tokenStore.readRefresh();
  if (!refreshToken) {
    tokenStore.clear();
    return;
  }
  try {
    const tokens = await userApi.refresh(refreshToken);
    tokenStore.save(tokens);
    setUser(tokens.user);
  } catch {
    tokenStore.clear();
    setUser(null);
  }
}
