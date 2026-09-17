import type { Directive, DirectiveBinding } from 'vue'

/**
 * v-permission="['user:create']"
 *
 * 配合后端 @RequiresPermissions 注解
 */
const permissionDirective: Directive<HTMLElement, string[]> = {
  mounted(el, binding: DirectiveBinding<string[]>) {
    const required = binding.value
    if (!required || required.length === 0) return

    const userPerms: string[] = JSON.parse(localStorage.getItem('jade_perms') || '[]')
    const has = required.some(p => userPerms.includes(p) || userPerms.includes('*'))

    if (!has) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default permissionDirective
