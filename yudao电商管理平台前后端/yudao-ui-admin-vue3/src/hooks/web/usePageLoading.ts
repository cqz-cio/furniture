import { useAppStoreWithOut } from '@/store/modules/app'

const appStore = useAppStoreWithOut()

export const usePageLoading = () => {
  const loadStart = (routePath = '') => {
    appStore.setPageLoading(true, routePath)
  }

  const loadDone = () => {
    appStore.setPageLoading(false)
  }

  return {
    loadStart,
    loadDone
  }
}
