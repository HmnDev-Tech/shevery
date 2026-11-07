# Shevery

<div align="center">

[![Stars](https://img.shields.io/github/stars/HmnDev-Tech/shevery?style=for-the-badge&color=yellow)](https://github.com/HmnDev-Tech/shevery/stargazers) [![Forks](https://img.shields.io/github/forks/HmnDev-Tech/shevery?style=for-the-badge&color=orange)](https://github.com/HmnDev-Tech/shevery/network/members) [![Downloads](https://img.shields.io/github/downloads/HmnDev-Tech/shevery/total?style=for-the-badge&color=green)](https://github.com/HmnDev-Tech/shevery/releases) [![Telegram](https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/hmndevtech)

</div>

[English](README.md) | [Русский](README.ru.md) | [Қазақша](README.kk.md) | [Qazaqşa (Latın)](README.kk-Latn.md) | [Português](README.pt.md) | [Español](README.es.md) | [العربية](README.ar.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md)

## Estado del fork

> [!IMPORTANT]
> **Acción de migración requerida:** debido al cambio del identificador de la app (`moe.shizuku.privileged.api` -> `com.hamondev.shevery`), **DEBES DESINSTALAR** cualquier versión antigua de la app oficial Shizuku Manager de tu dispositivo antes de instalar Shevery. De lo contrario, entrarán en conflicto.
> Referencia del proyecto original: <https://github.com/RikkaApps/Shizuku>
>

## Novedades del fork

- Interfaz del gestor en Jetpack Compose con componentes Material 3 Expressive, animaciones, interruptores e iconos redondeados.
- Función **«Comput» mejorada** basada en shell/adb, con explicaciones de Gemini, macros y creación de comandos mediante Commandium AI.
- Trabajo de compatibilidad con Android 16/17 usando SDK y herramientas de compilación actuales en este fork.
- Pantalla de módulos ADB para instalar y gestionar módulos ZIP.
- Funciones de los módulos: `module.prop`, banner, interruptor de activar/desactivar, `action.sh`, `service.sh` controlado por políticas, WebUI local, eliminación, comprobaciones de rutas, límites de tamaño, límites de salida y registros de las últimas ejecuciones.
- Ajustes de política de los módulos: modo seguro, acceso total y control de acciones en segundo plano.
- Catálogo de módulos con instalación directa.

## Documentación

- [Guía de módulos ADB](docs/adb-modules-guide.md)
- [Referencia de la API de módulos ADB](docs/adb-modules-api.md)
- [API de conectores Shizuku](docs/shizuku-connectors.md)
- [Compatibilidad con Android 17](docs/android-17-compatibility.md)
- [Guía de publicación de módulos ADB](docs/github-catalog.md)

## Antecedentes

Al desarrollar apps que necesitan root, el método más común es ejecutar algunos comandos en el shell su. Por ejemplo, existe una app que usa el comando `pm enable/disable` para activar/desactivar componentes.

Este método tiene desventajas muy grandes:

1. **Extremadamente lento** (creación de múltiples procesos)
2. Hay que procesar textos (**súper poco fiable**)
3. Las posibilidades están limitadas a los comandos disponibles
4. Incluso si ADB tiene permisos suficientes, la app necesita privilegios de root para funcionar

Shizuku utiliza un enfoque completamente distinto. Ver la descripción detallada más abajo.

## Guía del usuario y descarga

<https://shizuku.rikka.app/>

## Capturas de pantalla

<details>
  <summary>Haz clic para abrir las capturas de pantalla</summary>
  <br/>
  <table>
    <tr>
      <td align="center"><img src="screenshots/main.png" width="300" /><br/><b>Pantalla principal</b></td>
      <td align="center"><img src="screenshots/comput.png" width="300" /><br/><b>Consola Comput</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/modules.png" width="300" /><br/><b>Módulos ADB</b></td>
      <td align="center"><img src="screenshots/settings.png" width="300" /><br/><b>Ajustes</b></td>
    </tr>
  </table>
</details>

## ¿Cómo funciona Shevery?

Primero, tenemos que hablar de cómo las apps usan las API del sistema. Por ejemplo, si la app quiere obtener las apps instaladas, todos sabemos que debe usar `PackageManager#getInstalledPackages()`. En realidad se trata de un proceso de comunicación entre procesos (IPC) del proceso de la app con el proceso del servidor del sistema; el framework de Android simplemente hace el trabajo interno por nosotros.

Android usa el `binder` para este tipo de IPC. El `Binder` permite que el lado del servidor conozca el uid y el pid del lado del cliente, para que el servidor del sistema pueda comprobar si la app tiene permiso para realizar la operación.

Normalmente, si existe un «gestor» (por ejemplo, `PackageManager`) para que lo usen las apps, debe existir un «servicio» (por ejemplo, `PackageManagerService`) en el proceso del servidor del sistema. Podemos pensarlo de forma sencilla: si la app posee el `binder` del «servicio», puede comunicarse con el «servicio». El proceso de la app recibe los binders de los servicios del sistema al iniciarse.

Shizuku guía a los usuarios para que ejecuten primero un proceso, el servidor Shizuku, con root o ADB. Cuando la app se inicia, el `binder` del servidor Shizuku también se envía a la app.

La función más importante que ofrece Shevery es actuar como intermediario: recibir las solicitudes de la app, enviarlas al servidor del sistema y devolver los resultados. Pueden ver el método `transactRemote` en la clase `rikka.shizuku.server.ShizukuService` y la clase `moe.shizuku.api.ShizukuBinderWrapper` para más detalles.

Así alcanzamos nuestro objetivo: usar las API del sistema con permisos más altos. Y, para la app, es casi idéntico al uso directo de las API del sistema.

## Guía del desarrollador

### API y ejemplo

https://github.com/RikkaApps/Shizuku-API

### Migración desde versiones anteriores a v11

> Las aplicaciones existentes siguen funcionando, por supuesto.

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### Atención

1. Los permisos de ADB son limitados

   ADB tiene permisos limitados y distintos según la versión del sistema. Pueden ver los permisos concedidos a ADB [aquí](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml).

   Antes de llamar a la API, pueden usar `ShizukuService#getUid` para comprobar si Shizuku se ejecuta como usuario ADB, o usar `ShizukuService#checkPermission` para comprobar si el servidor tiene permisos suficientes.

2. Limitación de API ocultas desde Android 9

   A partir de Android 9, el uso de las API ocultas está limitado para las apps normales. Usen otros métodos (como <https://github.com/LSPosed/AndroidHiddenApiBypass>).

3. Android 8.0 y ADB

   Actualmente, el servicio Shizuku obtiene el proceso de la app combinando `IActivityManager#registerProcessObserver` e `IActivityManager#registerUidObserver` (26+) para garantizar que el proceso de la app se envíe al iniciarse. Sin embargo, en API 26 ADB carece de permisos para usar `registerUidObserver`; por eso, si necesitan usar Shizuku en un proceso que quizá no inicie una Activity, se recomienda provocar el envío del binder iniciando una activity transparente.

4. El uso directo de `transactRemote` requiere atención

   * La API puede ser distinta en diferentes versiones de Android; revísenla con atención. Además, `android.app.IActivityManager` existe en forma aidl en API 26 y posteriores, y `android.app.IActivityManager$Stub` existe solo en API 26.

   * `SystemServiceHelper.getTransactionCode` puede no obtener el código de transacción correcto; por ejemplo, `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` no existe en API 25, y allí existe `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` (este caso ya se ha tratado, pero no se excluye que pueda haber otras situaciones). Este problema no se presenta con el método `ShizukuBinderWrapper`.

## Desarrollo del propio Shizuku

### Compilación

- Clonen con `git clone --recurse-submodules`
- Ejecuten la tarea de Gradle `:manager:assembleDebug` o `:manager:assembleRelease`

La tarea `:manager:assembleDebug` genera un servidor depurable. Pueden adjuntar un depurador a `shizuku_server` para depurar el servidor. Tengan en cuenta que, en Android Studio, en «Run/Debug configurations» debe estar marcada la opción «Always install with package manager», para que el servidor use el código más reciente.

## Licencia

Todos los archivos de código de este proyecto están licenciados bajo Apache 2.0

## Créditos

* [kerneldroid](https://github.com/kerneldroid) - por partes de la interfaz de la app, del sistema de catálogo de módulos y del soporte de Android 17.
* [RikkaApps/Shizuku](https://github.com/rikkaapps/Shizuku) - por la Shizuku API y las fuentes principales.
* [Landon Moran](https://github.com/LandonMoran) - por correcciones de Dhizuku, TCP, etc.
* [DP-Hridayan](https://github.com/DP-Hridayan/aShellYou) - por partes de la interfaz «Comput».
* [protonpony/Shizuku-Keeper](https://github.com/protonpony/Shizuku-Keeper) - por el «Inicio automático al arrancar vía depuración inalámbrica».

## Y además...

¡Ahora pueden publicar su módulo ADB!
Sigan estos pasos:
- Creen su módulo.
- Creen un repositorio GH para este módulo.
- Añadan el tema «shevery-modules».
- Añadan las fuentes y el release con el módulo.

> [!CAUTION]
> **Aviso de app gratuita**
>
> Shevery es una app **completamente gratuita**. No paguen a gestores de archivos de terceros ni a servicios en la nube para descargar esta aplicación.
