const config: {
  base_url: string
  ai_base_url?: string
  result_code: number | string
  default_headers: AxiosHeaders
  request_timeout: number
} = {
  /**
   * api请求基础路径
   */
  base_url: import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL,
  /**
   * 独立 AI 服务地址。未配置时继续走统一后端/网关。
   */
  ai_base_url: import.meta.env.VITE_AI_BASE_URL
    ? import.meta.env.VITE_AI_BASE_URL + import.meta.env.VITE_API_URL
    : undefined,
  /**
   * 接口成功返回状态码
   */
  result_code: 200,

  /**
   * 接口请求超时时间
   */
  request_timeout: 30000,

  /**
   * 默认接口请求类型
   * 可选值：application/x-www-form-urlencoded multipart/form-data
   */
  default_headers: 'application/json'
}

export { config }
