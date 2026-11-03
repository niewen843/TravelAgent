import type { ReactNode } from 'react'
import { Brand } from '../../../shared/ui/Brand'
import { TravelIllustration } from './TravelIllustration'
import '../auth.css'

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="auth-layout">
      <section className="travel-story">
        <Brand />
        <div className="story-copy">
          <h1>
            下一站，
            <br />
            按你的节奏。
          </h1>
          <p>
            从一个想法开始，把想去的地方、
            <br className="desktop-break" />
            想留的时间，写成自己的旅行。
          </p>
        </div>
        <TravelIllustration />
        <p className="story-footnote">一份属于你的行程，随时回来接着计划。</p>
      </section>
      <section className="auth-panel">
        <div className="mobile-brand">
          <Brand />
        </div>
        {children}
        <p className="auth-footer">你的旅行与对话，仅在你的账号中保存。</p>
      </section>
    </main>
  )
}
