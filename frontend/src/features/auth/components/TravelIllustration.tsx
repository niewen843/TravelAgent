/** Local vector artwork: a riverside walk connects a bridge, a pavilion and a quiet lake. */
export function TravelIllustration() {
  return (
    <svg
      className="travel-illustration"
      viewBox="0 0 600 330"
      role="img"
      aria-label="桥、湖和亭子之间的旅行路线插画"
    >
      <path
        d="M-20 230C95 154 153 243 262 200s221-39 357-90v240H-20Z"
        fill="#d1e5e9"
      />
      <path
        d="M-20 283C99 205 169 299 302 243s199-34 318-59"
        fill="none"
        stroke="#afcfd9"
        strokeWidth="2"
      />
      <path
        d="M15 209c91-6 103-122 184-109s38 131 136 124 67-145 179-123"
        fill="none"
        stroke="#348499"
        strokeWidth="2"
        strokeDasharray="5 8"
      />
      <g stroke="#173f54" fill="none" strokeWidth="2.5">
        <path d="M38 192h124M53 193v-40m94 40v-40M53 153c30 26 64 26 94 0M53 166c30 27 64 27 94 0" />
        <path d="M53 152v-20m94 20v-20" />
        <path d="M351 207h99m-83 0v-48m64 48v-48M351 159h99l-50-28-49 28Zm13-8h73M400 131v-12" />
        <path d="M372 208v-27h17v27m13 0v-27h17v27" />
      </g>
      <path d="m205 255 34-16 36 16-35 12Z" fill="#75a9b4" />
      <path
        d="M239 239v-40l30 34Z"
        fill="#f7fbfc"
        stroke="#173f54"
        strokeWidth="1.5"
      />
      <g fill="#173f54">
        <circle cx="15" cy="209" r="4" />
        <circle cx="199" cy="100" r="4" />
        <circle cx="335" cy="224" r="4" />
        <circle cx="514" cy="101" r="4" />
      </g>
      <circle
        cx="514"
        cy="101"
        r="15"
        fill="none"
        stroke="#348499"
        strokeWidth="1.5"
      />
      <g fontFamily="inherit" fontSize="12" fill="#486d7c">
        <text x="64" y="225">
          沿江走走
        </text>
        <text x="361" y="238">
          留一点空闲
        </text>
        <text x="471" y="72">
          下一站
        </text>
      </g>
      <g stroke="#6d9fad" fill="none" strokeWidth="1.5">
        <path d="m267 86 9-4 9 4m-54-19 9-4 9 4" />
        <path d="M483 252h48m-24 9h39M75 272h43" />
      </g>
    </svg>
  )
}
