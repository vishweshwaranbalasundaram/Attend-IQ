export default function CampusBackdrop() {
  return (
    <div className="campus-backdrop" aria-hidden="true">
      <svg viewBox="0 0 1600 1000" preserveAspectRatio="xMidYMax slice" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="campus-sky" x2="0" y2="1">
            <stop stopColor="#dcebe1" />
            <stop offset=".55" stopColor="#f2e7d5" />
            <stop offset="1" stopColor="#e9e9dc" />
          </linearGradient>
          <linearGradient id="campus-glass" x2="1" y2="1">
            <stop stopColor="#d7e8d8" />
            <stop offset=".48" stopColor="#9fbdb0" />
            <stop offset="1" stopColor="#718f83" />
          </linearGradient>
          <linearGradient id="campus-wall" x2="0" y2="1">
            <stop stopColor="#f7f1e4" />
            <stop offset="1" stopColor="#d5d9ca" />
          </linearGradient>
          <linearGradient id="campus-path" x2="0" y2="1">
            <stop stopColor="#d9d4c2" />
            <stop offset="1" stopColor="#b9b8a6" />
          </linearGradient>
          <radialGradient id="campus-sun">
            <stop stopColor="#fff8d9" stopOpacity=".95" />
            <stop offset="1" stopColor="#f5d6a0" stopOpacity="0" />
          </radialGradient>
          <filter id="campus-soft-shadow" x="-30%" y="-30%" width="160%" height="180%">
            <feGaussianBlur in="SourceAlpha" stdDeviation="13" />
            <feOffset dy="15" />
            <feComponentTransfer><feFuncA type="linear" slope=".2" /></feComponentTransfer>
            <feMerge><feMergeNode /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
          <pattern id="campus-windows" width="48" height="42" patternUnits="userSpaceOnUse">
            <rect x="7" y="7" width="25" height="23" rx="3" fill="#eaf1dc" fillOpacity=".76" />
            <path d="M19.5 7v23M7 18.5h25" stroke="#718f83" strokeOpacity=".33" />
          </pattern>
        </defs>
        <rect width="1600" height="1000" fill="url(#campus-sky)" />
        <circle cx="1260" cy="170" r="205" fill="url(#campus-sun)" />
        <circle cx="1260" cy="170" r="49" fill="#fff5d5" fillOpacity=".7" />
        <g fill="#fff" fillOpacity=".46">
          <path d="M145 203c22-39 77-41 101-5 41-15 80 15 73 51H121c-8-22 3-40 24-46Z" />
          <path d="M1050 277c18-29 59-31 77-4 30-11 60 11 55 38H1032c-6-17 2-30 18-34Z" />
        </g>
        <path d="M0 495c176-76 304-40 459-91 166-55 285 8 425-27 188-48 322 26 456-12 103-29 185-13 260 17v288H0Z" fill="#a6bba3" fillOpacity=".6" />
        <path d="M0 565c178-50 337-7 487-58 188-64 316 19 485-18 205-45 383 19 628-35v260H0Z" fill="#789681" fillOpacity=".42" />
        <g fill="#91aa96" fillOpacity=".66">
          <path d="M180 394h97v142h-97zM283 425h82v111h-82zM1240 394h108v142h-108zM1355 425h80v111h-80z" />
          <path d="M171 382h115v15H171zM1231 382h125v15h-125z" fill="#e2e4d4" />
          <path d="M198 414h60v93h-60zM1258 414h70v93h-70z" fill="url(#campus-windows)" />
        </g>
        <path d="M0 590c235-48 439-12 637-28 248-20 440-36 963 4v434H0Z" fill="#c6d0bb" />
        <path d="M0 689c260-65 443-30 677-61 276-36 564-44 923 0v372H0Z" fill="#a9bea4" />
        <path d="M540 1000c110-113 182-220 256-335 75 110 167 215 279 335Z" fill="url(#campus-path)" opacity=".86" />
        <path d="M704 1000c50-115 70-220 92-335 30 115 56 220 104 335Z" fill="#eee7d4" fillOpacity=".67" />
        <path d="M0 840c194-54 376-52 554-21l-77 181H0ZM1600 827c-174-34-338-23-506 5l102 168h404Z" fill="#7e9e7d" fillOpacity=".62" />
        <g filter="url(#campus-soft-shadow)">
          <path d="M473 386 768 278l359 105v263L768 751 473 652Z" fill="url(#campus-wall)" />
          <path d="m768 278 359 105v263L768 751Z" fill="#bdcbbd" />
          <path d="m473 386 295-108v61L473 451Z" fill="#6f8e7c" />
          <path d="m768 339 359 105v-61L768 278Z" fill="#8ca995" />
          <path d="M512 429 748 345v318l-236-79Z" fill="url(#campus-glass)" />
          <path d="m790 359 298 88v302l-298-91Z" fill="url(#campus-windows)" />
          <path d="m512 429 236-84v42l-236 84Z" fill="#e8e9d8" />
          <path d="m748 345 340 102v43l-340-103Z" fill="#e3e5d7" />
          <path d="M629 388v276M688 367v276M512 535l236 79M512 587l236 79" stroke="#f4f3e8" strokeOpacity=".76" strokeWidth="5" />
          <path d="M800 427v290M868 447v290M936 467v290M1004 487v290M790 520l298 88M790 579l298 88M790 638l298 88" stroke="#eef1e4" strokeOpacity=".56" strokeWidth="4" />
          <path d="m741 662 28-12v101l-28 11ZM769 650l30 9v102l-30-8Z" fill="#496d5c" />
          <path d="m727 661 42-16 44 14-44 17Z" fill="#f3eedc" />
        </g>
        <g fill="#526f57">
          <path d="M123 699c0-47 20-84 47-84s47 37 47 84v95h-94z" />
          <path d="M1360 695c0-53 24-95 54-95s54 42 54 95v103h-108z" />
          <path d="M316 740c0-34 16-61 36-61s36 27 36 61v63h-72z" />
          <path d="M1184 731c0-40 18-73 41-73s41 33 41 73v73h-82z" />
        </g>
        <g fill="#718d68">
          <circle cx="170" cy="643" r="60" /><circle cx="1414" cy="638" r="70" />
          <circle cx="351" cy="694" r="47" /><circle cx="1225" cy="678" r="52" />
        </g>
        <g fill="#d9e2c6" fillOpacity=".53">
          <circle cx="146" cy="628" r="19" /><circle cx="1391" cy="617" r="23" />
          <circle cx="336" cy="681" r="15" /><circle cx="1208" cy="661" r="17" />
        </g>
        <path d="M0 965c278-52 415-21 569 35H0ZM1600 940c-215-40-356-9-488 60h488Z" fill="#667f61" fillOpacity=".43" />
      </svg>
      <div className="campus-backdrop-glow" />
    </div>
  );
}
