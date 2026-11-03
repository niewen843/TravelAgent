import { Link } from 'react-router-dom'
import { useAuth } from '../auth/auth-context'
import { AccountLayout } from '../../shared/ui/AccountLayout'
import { TravelIllustration } from '../auth/components/TravelIllustration'

export function AccountPage() {
  const { account } = useAuth()
  return (
    <AccountLayout>
      <section className="account-welcome">
        <div>
          <p className="quiet-label">你的旅行手册</p>
          <h1>你好，{account?.nickname}。</h1>
          <p>
            账号已准备好。旅行工作台正在开发中，
            <br />
            下一程的想法，很快就能在这里展开。
          </p>
          <Link className="button secondary" to="/account/password">
            修改密码
          </Link>
        </div>
        <TravelIllustration />
      </section>
      <section className="account-details">
        <h2>账号信息</h2>
        <dl>
          <div>
            <dt>用户名</dt>
            <dd>{account?.username}</dd>
          </div>
          <div>
            <dt>昵称</dt>
            <dd>{account?.nickname}</dd>
          </div>
          <div>
            <dt>账号类型</dt>
            <dd>{account?.role === 'ADMIN' ? '管理员' : '普通用户'}</dd>
          </div>
        </dl>
        <p>
          旅行内容与对话按账号分别保存。管理员可通过管理后台查看数据，用于问题排查。
        </p>
      </section>
    </AccountLayout>
  )
}
