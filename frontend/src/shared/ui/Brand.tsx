import { Link } from 'react-router-dom'

export function Brand() {
  return (
    <Link className="brand" to="/" aria-label="行笺首页">
      <span className="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 32 32">
          <path
            d="M8 23 16 7l8 16-8-4-8 4Z"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.7"
          />
          <path d="M16 7v12" stroke="currentColor" strokeWidth="1.7" />
        </svg>
      </span>
      <span>
        行笺<small>把旅行，慢慢写好</small>
      </span>
    </Link>
  )
}
