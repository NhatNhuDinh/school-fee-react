"use client";

import { useState } from "react";

import { useLogin } from "@refinedev/core";
import { GraduationCap } from "lucide-react";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { InputPassword } from "@/shared/components/form/input-password";

/**
 * The sign-in screen: the application's headline on the left and a frosted-glass
 * card on the right, over a full-bleed light-blue background.
 *
 * This is the layout of the Jmix application it replaces, and it is one of the
 * places where a surface is a picture rather than a token. It is the only
 * screen somebody sees before they are inside anything, so it has to say whose
 * system this is on its own. The colours are therefore this screen's own and
 * deliberately do not follow the theme presets.
 *
 * Username *or* email, because the API accepts either. There is no
 * self-registration and no password-reset link: accounts are created by an
 * administrator on the Users screen, and a forgotten password is reset there
 * too.
 */
export const SignInForm = () => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const { mutate: login, isPending } = useLogin();

  const handleSignIn = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    login({ username, password });
  };

  return (
    <div
      className="relative flex min-h-svh w-full flex-col justify-between bg-[#cfe6fb] bg-cover bg-center bg-no-repeat"
      style={{ backgroundImage: "url('/images/login-bg.jpg')" }}
    >
      {/* A light veil on the left so the navy headline stays readable over the
          picture. It must never intercept a click meant for the form. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[linear-gradient(90deg,rgba(255,255,255,0.5)_0%,rgba(255,255,255,0.15)_45%,rgba(255,255,255,0)_65%)] max-[900px]:bg-white/35"
      />

      <div className="relative h-8" />

      <main className="relative mx-auto flex w-full max-w-[1180px] items-center justify-between gap-16 px-12 py-6 max-[900px]:flex-col max-[900px]:justify-center max-[900px]:gap-6 max-[900px]:px-4">
        <section className="flex max-w-[520px] flex-col gap-5 max-[900px]:items-center max-[900px]:text-center">
          <div className="flex size-14 items-center justify-center rounded-xl bg-[linear-gradient(135deg,#1d4ed8_0%,#062248_100%)] text-white shadow-[0_12px_24px_rgba(6,34,72,0.25)]">
            <GraduationCap className="size-7" aria-hidden />
          </div>
          <h1 className="text-[38px] leading-[1.15] font-bold tracking-[-0.01em] text-[#0b2a55] uppercase max-[900px]:text-[30px]">
            Hệ thống thu
            <br />
            phí trường học
          </h1>
          <p className="max-w-[460px] text-[17px] leading-relaxed text-slate-700">
            Quản lý khoản thu, đợt thu, thông báo và chứng từ thu tiền của học
            sinh trên một nền tảng duy nhất.
          </p>
        </section>

        <section className="w-[400px] shrink-0 rounded-2xl border border-white/85 bg-white/60 pt-2 pb-3 shadow-[0_30px_60px_rgba(11,42,85,0.18),inset_0_1px_0_rgba(255,255,255,0.9)] backdrop-blur-[18px] backdrop-saturate-[140%] max-[900px]:w-full max-[900px]:max-w-[400px]">
          <form
            onSubmit={handleSignIn}
            className="flex flex-col gap-4 px-6 pt-5 pb-2 [&_button[aria-label]]:text-slate-500 [&_button[aria-label]:hover]:text-slate-800"
          >
            <h2 className="text-[22px] font-bold text-[#0b2a55]">Đăng nhập</h2>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="username" className="text-[13px] font-semibold text-slate-700">
                Tên đăng nhập <span aria-hidden>*</span>
              </Label>
              <Input
                id="username"
                name="username"
                type="text"
                autoComplete="username"
                autoFocus
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="min-h-[38px] border-[#0f264b]/15 bg-white/85 text-slate-900 shadow-none placeholder:text-slate-400 focus-visible:border-blue-600 focus-visible:bg-white focus-visible:ring-[3px] focus-visible:ring-blue-600/15"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password" className="text-[13px] font-semibold text-slate-700">
                Mật khẩu <span aria-hidden>*</span>
              </Label>
              <InputPassword
                id="password"
                name="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="min-h-[38px] border-[#0f264b]/15 bg-white/85 text-slate-900 shadow-none placeholder:text-slate-400 focus-visible:border-blue-600 focus-visible:bg-white focus-visible:ring-[3px] focus-visible:ring-blue-600/15"
              />
            </div>

            <button
              type="submit"
              disabled={isPending}
              className="mt-1 min-h-10 w-full rounded-lg bg-[linear-gradient(135deg,#2563eb_0%,#1d4ed8_100%)] text-sm font-semibold text-white shadow-[0_10px_20px_rgba(37,99,235,0.28)] transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {isPending ? "Đang đăng nhập…" : "Đăng nhập"}
            </button>
          </form>
        </section>
      </main>

      <footer className="relative flex w-full justify-center px-12 pb-6 text-xs text-slate-600">
        © 2026 Hệ thống thu phí trường học
      </footer>
    </div>
  );
};

SignInForm.displayName = "SignInForm";
