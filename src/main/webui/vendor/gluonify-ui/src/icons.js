import {
  ArrowDown, ArrowLeft, ArrowRight, ArrowUp, BookOpen, Check, CheckCircle2, ChevronDown, ChevronLeft, ChevronRight, ChevronUp,
  ChevronsUpDown, CircleHelp, Clock, ArrowLeftRight, FilePen, Folder, History, Copy, Download, ExternalLink, Eye, EyeOff, Filter, House, Info, KeyRound, Link2, List, Loader2, Lock,
  LockOpen, LogIn, LogOut, Menu as MenuIcon, Minus, Moon, Pause, Pencil, Play, Plus, RefreshCw, Save, Search, Settings,
  ShieldCheck, SlidersHorizontal, Rocket, ScrollText, Sun, Trash2, TriangleAlert, Upload, User, Wand2, X, XCircle,
} from 'lucide-vue-next'
import {
  Ban, BarChart3, Circle, CirclePlay, CirclePlus, CircleStop, CloudUpload, Code, Database, DollarSign, File as FileIcon, FileCheck,
  Flag, FolderOpen, FolderPlus, Inbox, LayoutGrid, ListChecks, Mail, Network, RefreshCcw, RotateCcw, Send, Share2, Signpost, Table2, Tag,
  Undo2, Zap, Asterisk, Star, Eraser, Image as ImageIcon, Maximize, Maximize2, Minimize2, Fullscreen, Expand, Package, Server,
  LayoutDashboard, Boxes, Route, Globe, Waypoints, Hammer, Gauge, Activity, HardDrive, Users, UserCog,
} from 'lucide-vue-next'

/** Semantic names of the common icon set (Lucide) -> components. The names are the contract; add here, never in a console. */
export const ICONS = {
  signIn: LogIn, signOut: LogOut, logout: LogOut, check: Check, eye: Eye, eyeOff: EyeOff, sun: Sun, moon: Moon, menu: MenuIcon,
  lock: Lock, unlock: LockOpen, user: User, key: KeyRound, shield: ShieldCheck, chevronDown: ChevronDown, chevronUp: ChevronUp,
  chevronLeft: ChevronLeft, chevronRight: ChevronRight, arrowLeft: ArrowLeft, arrowRight: ArrowRight, arrowUp: ArrowUp,
  arrowDown: ArrowDown, close: X, plus: Plus, minus: Minus, trash: Trash2, pencil: Pencil, refresh: RefreshCw, search: Search,
  filter: Filter, settings: Settings, sliders: SlidersHorizontal, info: Info, warning: TriangleAlert, ok: CheckCircle2, fail: XCircle,
  spinner: Loader2, copy: Copy, download: Download, upload: Upload, externalLink: ExternalLink, home: House, book: BookOpen,
  swap: ArrowLeftRight, fileEdit: FilePen, folder: Folder, history: History, sortNone: ChevronsUpDown, sortAsc: ArrowUp, sortDesc: ArrowDown,
  link: Link2, list: List, clock: Clock, help: CircleHelp, play: Play, pause: Pause, save: Save, rocket: Rocket, wand: Wand2, audit: ScrollText,
  // used by the workflow and data consoles
  ban: Ban, bolt: Zap, chart: BarChart3, dot: Circle, cloudUpload: CloudUpload, code: Code, database: Database, dollar: DollarSign,
  file: FileIcon, fileCheck: FileCheck, folderOpen: FolderOpen, folderPlus: FolderPlus, listCheck: ListChecks, replay: RotateCcw,
  share: Share2, workflow: Network, sync: RefreshCcw, table: Table2, grid: LayoutGrid, asterisk: Asterisk, playCircle: CirclePlay,
  plusCircle: CirclePlus, stopCircle: CircleStop, flag: Flag, inbox: Inbox, envelope: Mail, send: Send, directions: Signpost, tag: Tag,
  undo: Undo2,
  // used by the graph database console
  star: Star, eraser: Eraser, image: ImageIcon, fit: Maximize, maximize: Maximize2, minimize: Minimize2, fullscreen: Fullscreen,
  exitFullscreen: Expand, package: Package, server: Server,
  // used by the administration console (navigation)
  dashboard: LayoutDashboard, boxes: Boxes, route: Route, globe: Globe, waypoints: Waypoints, hammer: Hammer, gauge: Gauge,
  activity: Activity, hardDrive: HardDrive, users: Users,
  // signed-in user menu: Charm account security
  userCog: UserCog,
}

/** Accepts a kit name or its kebab-case spelling (`sign-in`); undefined when unknown (nothing is drawn rather than a wrong glyph). */
export function resolveIcon(ref) {
  if (!ref) return undefined
  const name = String(ref).replace(/-([a-z0-9])/g, (_, c) => c.toUpperCase())
  return Object.prototype.hasOwnProperty.call(ICONS, name) ? name : undefined
}
