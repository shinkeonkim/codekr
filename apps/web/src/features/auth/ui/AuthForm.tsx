"use client";

import type { TokenResponse } from "@/entities/user";
import { useAuth } from "../model/AuthProvider";
import { ApiError } from "@/shared/api";
import { readNextParam } from "@/shared/lib";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { useState } from "react";
import type { FormEvent } from "react";

import { Alert, BrandCharacter, Button, Card, Field, Input } from "@/shared/ui";
import type { BrandCharacterName } from "@/shared/ui";

export interface AuthFormField {
  name: string;
  label: string;
  type: string;
  placeholder?: string;
  autoComplete?: string;
}

interface Props {
  /**
   * 함께 보일 그림 (#261).
   *
   * **로그인 화면에는 두지 않는다.** 매번 지나는 길목이라 그림이 금방 지겨워지고,
   * 여기서 하려는 일은 빨리 들어가는 것이다. 가입처럼 **한 번뿐인 자리**에만 둔다.
   */
  mascot?: BrandCharacterName;
  title: string;
  description: string;
  fields: AuthFormField[];
  submitLabel: string;
  footer: { text: string; href: string; linkLabel: string };
  /** 폼 아래에 덧붙일 것. 로그인 화면의 "비밀번호를 잊으셨나요" 가 그것이다 (#315). */
  aside?: ReactNode;
  onSubmit: (values: Record<string, string>) => Promise<TokenResponse>;
}

/** 로그인과 회원가입이 공유하는 폼. 다른 것은 필드 목록과 제출 동작뿐이다. */
export function AuthForm({
  mascot,
  title,
  description,
  fields,
  submitLabel,
  footer,
  aside,
  onSubmit,
}: Props) {
  const router = useRouter();
  const { signIn } = useAuth();
  const [values, setValues] = useState<Record<string, string>>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    try {
      const tokens = await onSubmit(values);
      signIn(tokens);
      // 가려던 곳이 있으면 그리로 돌려보낸다 (#113).
      // safeNextPath 가 외부 주소를 걸러낸다 — 오픈 리다이렉트가 되면 안 된다.
      router.push(readNextParam());
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message);
        setFieldErrors(Object.fromEntries(caught.fieldErrors.map((it) => [it.field, it.message])));
      } else {
        setError("서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-md py-8">
      <Card className="p-6">
        {mascot ? <BrandCharacter name={mascot} size={200} className="mx-auto mb-2" /> : null}
        <h1 className="text-xl font-bold text-ink">{title}</h1>
        <p className="mt-1 text-sm text-ink-muted">{description}</p>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          {error ? <Alert>{error}</Alert> : null}

          {fields.map((field) => (
            <Field key={field.name} label={field.label} error={fieldErrors[field.name]}>
              <Input
                name={field.name}
                type={field.type}
                placeholder={field.placeholder}
                autoComplete={field.autoComplete}
                required
                value={values[field.name] ?? ""}
                onChange={(event) =>
                  setValues((previous) => ({ ...previous, [field.name]: event.target.value }))
                }
              />
            </Field>
          ))}

          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? "처리 중…" : submitLabel}
          </Button>
        </form>

        {aside ? <div className="mt-3 text-center text-xs">{aside}</div> : null}

        <p className="mt-6 text-center text-sm text-ink-muted">
          {footer.text}{" "}
          <Link href={footer.href} className="font-medium text-brand hover:underline">
            {footer.linkLabel}
          </Link>
        </p>
      </Card>
    </div>
  );
}
