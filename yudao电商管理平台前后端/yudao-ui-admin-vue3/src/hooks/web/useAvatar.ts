import { computed, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import defaultAvatar from '@/assets/imgs/avatar.gif'
import { resolveAvatarUrl } from '@/utils/avatar'

/** Keep a failed remote avatar editable, and recover when the user chooses a new one. */
export function useAvatar(value: MaybeRefOrGetter<string | undefined>) {
  const source = computed(() => resolveAvatarUrl(toValue(value)))
  const failed = ref(false)
  watch(source, () => (failed.value = false), { flush: 'sync' })
  const cropSource = computed(() => (failed.value ? '' : source.value))
  const avatar = computed(() => cropSource.value || defaultAvatar)
  const handleAvatarError = () => {
    failed.value = true
  }
  return { avatar, cropSource, handleAvatarError }
}
