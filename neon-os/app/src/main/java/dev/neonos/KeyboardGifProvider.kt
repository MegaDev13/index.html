package dev.neonos

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import java.io.FileNotFoundException

class KeyboardGifProvider : ContentProvider() {
    override fun onCreate() = true

    private fun id(uri: Uri) = uri.pathSegments.singleOrNull()?.takeIf(data::containsKey)

    override fun getType(uri: Uri): String? = if (id(uri) != null) "image/gif" else null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Somente leitura.")
        val bytes = Base64.decode(data[id(uri)] ?: throw FileNotFoundException(), Base64.DEFAULT)
        return openPipeHelper(uri, "image/gif", null, bytes) { output, _, _, _, _ ->
            ParcelFileDescriptor.AutoCloseOutputStream(output).use { it.write(bytes) }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
                       sortOrder: String?): MatrixCursor? {
        val name = id(uri) ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val length = Base64.decode(data.getValue(name), Base64.DEFAULT).size
        return MatrixCursor(columns).apply {
            addRow(columns.map { when (it) {
                OpenableColumns.DISPLAY_NAME -> "$name.gif"
                OpenableColumns.SIZE -> length
                else -> null
            } })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        internal val items = listOf("neon-pulse" to "◉  PULSO NEON", "access-granted" to "✓  ACESSO LIBERADO",
            "ghost-glitch" to "▰  GHOST GLITCH")
        internal fun uri(context: Context, id: String) =
            Uri.Builder().scheme("content").authority(context.packageName + ".keyboard-gif").appendPath(id).build()

        // Original frame-by-frame pixel animations. No network catalogue or SDK.
        private val data = mapOf(
            "neon-pulse" to """
                R0lGODlhUABAAIIAADj/3xlicuZE/xM1QgMJEgAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACQAAACwAAAAAUABAAAAI/wAJCBxIsK
                DBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIUeCAhx8dhmw4kmHJhScVpky4EmHLgy8Nxiw4M4DNmzdRgjQ5oKfPn0CDAsUptCfRokiLktxJ0KZL
                hE4JzpTKdKnAqE8TYp06kKvHnVhZMnTqlUDZswFEOgxwtupYtwvTqp07tq1IuSbhQjWrVyxelX0LyrVr9W9Wq1q7BoY50LDMxQT+Es7bGDDdg5
                Ih02xqWWVSo59DixbK9jNig4Yn6+R8mDLqx5dXs4Z9erbi2J0rM8YdeXft3Lo383bMl3fr4FSH+3bNHPnt371pKwRAnTp04qqjS0dYvXtz58V/
                E/8Pz526gPPVZQveDly78PIAzstPLxYz+/q2k5eXz986/vXvsYRUaaP5ZB5/6AFQ4E8EBiXecQUdiKB/EF51H4TjxVTdhBQulx956lnooUEbJg
                hAiOBllyFD3dHXnoj6fefec9N5JyN2feFo3IgzfqXchTxWqON1QRZ535Ay9pjdfzAGiGKTPkKHYYVUzrjkceOBCFNoDS7oZVBdCiVlk1cul6WW
                TAIYZZJmvlgljGXSdiaab4oYp3Bz0mlkZXdSlaeeQArWZ1d/AuqkQoUa+mGajLpUqFdsaRajTmHVGVlag64pW6WHNoVXporSeCmVlfb55YA3oW
                rTqWKyGeilOOUa5Oaek876aq2NdqTrrrz26uuvwAYr7LAZBQQAIfkEAQkABQAsIAAYABEAEQCCOP/fGWJy5kT/EzVCAwkSAAAAAAAAAAAACGMA
                CwgcCKBgwYEICRpcCCChQIMJISI8WIAAgQEWHzbUKNDiRY8cCxwE2THjwZEZEYJcWJGAw5YiIQ4Y8HJmTJQvTUIkOXBlQ5QuS7qkSNEjxpQUY5
                Y8GlLhRqcvGbJ8qVEiwoAAIfkEAQkABQAsHQAVABcAFwCCOP/fGWJy5kT/EzVCAwkSAAAAAAAAAAAACI4ACwgcKBCAwYMACCpUiLChwYUMHy48
                CLEARYEECAzIOPAiQYkZM24MWTDhR5MkCaaUaBElgYoFOLYsGfMlzJozQdqEKRMhzpsYX/qUCZSjzwEDgApE2vIh0ZtGKT7lKfTi1IU9EzrdWT
                EqzZ8Qs3Z0ybWmTZZfQ2pU+/WkyZojx76NOFcuUIcNlZbMCzEgACH5BAEJAAUALBoAEgAdAB0Agjj/3xlicuZE/xM1QgMJEgAAAAAAAAAAAAi3
                AAsIHEiwAICDCAEUXMjQYMKHCBsyTDgxokSBFgsQ2DhgI4GBGRda9MiR5EeHCkUeFOiRYUuUKjVuvCjz40qCN2fSlIkxpUOWJ3fy/PlTp1CgRF
                caPTpzpdKgR5FSXBq1aUSqTG1GHDAgKkGuKBViFWr1qdeBZcVCraq16NqdVnsOZZs05diGOm8SvVswr0+5LyW21Asy59iXIQvbJdmRZM+/BTN6
                bBw0cUXCiiFfhMj57GOKFwMCACH5BAEJAAUALBcADwAjACMAgjj/3+ZE/xlichM1QgMJEgAAAAAAAAAAAAjkAAsIHEhwIICDCBEWXMhwYcKHCR
                tKNBjRYcWJBC8S2DhgIwGKBzEKVFjAo8eOJj8WICmR5ICXAxjCjLkyZEObHkWW3DgSAEOFOXXu/MiS4lChA3PaNLoTKUGeNTP6hOo0KVGfPZtW
                LcjTZkiqWwV29QlUZVixV8ue5Zp2qtm1TSOCXTs25Eu4A+/KfUu3rVa8dd3iRVtTLdy6UeduHZtVcVXGjfk6hZz172SVSyNLxqgUq1S3mxt29i
                wVbWi2mEkXdAlTZuvCGEmmJIDSZE/VN1meDFoY98SHPy9WhUh8MPHMEgMCACH5BAEJAAUALBQADAApACkAgjj/3+ZE/xlichM1QgMJEgAAAAAA
                AAAAAAj/AAsIHEiwYAEACBMmNMiwYUOFECM6nFgQokOLFB8uFEigI4EBHgkMVJixIsKBITuCTDnyZMmDLlkalLmRYkyPJUMKdHkRAMeOL1EChd
                lT4ICjQQkeHbDTp8GFQ5MWBFqzZQGcUqdSdWr1qsisM7/yJBoVrFaiTX+aZTjU5UmQaxt+RPv2a9ypTE+e/Gj3Lkq4JOf6NQgYKtPBBQvvPYx4
                oGKfSBs7zmtYsmORgRlLfnxVc2POfC2H1gu57+C5bkuLppzWa+O2XPeajruV4M3TYrmmxboW59imPsuC3aq761Kwx9EyvDmbItbfT53qzDgd+n
                Lmwn8+t66Ra0q+MmEWFs9IUjv4qOWzYrxe1WzE99zXwh8/MSAAIfkEAQkABQAsEQAJAC8ALwCCOP/f5kT/GWJyEzVCAwkSAAAAAAAAAAAACP8A
                CwgcSLAgQQAIEyY0yLChw4MKI0oE8LAiQ4kOMVqsGHEggY8EBoAkMLDjxosIPY78KHIlSYELT0KkWGClQ5sFYp6MOVJmz5wpLfL8KJMgSJhBHw
                Y9WtQoUaAcaT5tavBpUoNLX1JlaJVmwYUDBmx9GBao15IUQ2odW1UkVIgCQ7Il+/JqyqlzGxJNuhBv3qokY95d+5drYJqDCz/cizgtYcVOoSaG
                fPPwZMqGzTrGrNfyZs6ANRcoC7pgWYU1H4NmfLl0XM+pXUdG7bc0Y6i1Md/GrZry7tarDyP9HNws2ti2hR+vKZaz27fH1ep2e3X46OaQT1dH+h
                py14xSezc9/a40vHiLTLdjzXq+cl316823d9r1rFD2uV+nhx/Vq0u1LqFlX1EmpcbST5oNSJVGKKEG2UQQ8ldYhFsFBAAh+QQBCQAEACwOAAYA
                NQA1AIE4/9/mRP8TNUIDCRII/wAJCBxIsKBBAAgTKgRgsKHDhw8XSpQIsaLFiREpWtwocCHBASAHCAg5gKBHjhAVCiRJciRLkB0TonQok8DLhz
                cJ1JwZk6FNkihZ9uRZMyTPjzB1IkRZNOnRgUaV+qwo0+VThyAFSL3IUGTJq1hHKqXqUyRYiGLH0iz79SzOr0sPLnXq9u3WgksF6K1bUa/WuANl
                0uWLteTOsYMJF1Yb86diizABz038uKDRuJMrQzbsM7NmiJEZCm77uWHo0aUrnu5KOrXB1Y5dL1ZJWbZjlXttG9xLu7Xu26h/I5XKWvjw3sahcg
                4u/DRi37JDN66dWvpz6KUvT/Ws2/p07J+9b2ul/ti5SYZ+ZacHHJh79uUN45J3m5Q93rJaNae1L9dmWsVeMZYRevPNlNVdZLEFHkdRHZbgSgW+
                BRd/D/4U1UZCIUiUfBmalqGDR6lkIUsu5SSiWydBmBVQ7YF4FkZrpajZRDRSWFqNNm4UEAA7
            """,
            "access-granted" to """
                R0lGODlhUABAAIIAADj/3xtwcBM1QgcpKwMJEgAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACQAAACwAAAAAUABAAAAI/wAJCBxIsK
                DBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIUaCAhx8dhmw4kmHJhScVpky4EmHLgy8Nxiw4k2DNgTc9mhTAs6fPn0CDCh1KtGhPkiCTilSKdKnT
                plBNKgRAtarVq1izas2qkmnBAGDDih1LtqzZs2BdeiWItq3btwHUPiVYdYDdu3jz6t3Ld29VmGsFUu1LuLDhu1QBzx04+LDjx3YTywxMoDHky4
                Ql06RsGbPnvJptcgbwuTTe0Dh3Du1s2jNVolEZk25NWEBmAIpjC55Ne2/Pvqh1Lt7d27dPvsEJ5FR+kHXxn8BxTx5emXfxAdCj55Zq0Hnr7Nqn
                6/+ufh378cLJl6/0/hn87e0om1s37f69eO4F2d+1bbi+/c3U6Wcef335959oKBGln4F2MRgdbPjRNV9eBjoYHoDjCTggTxSed1l6o/XnoYUHph
                bghMb9RmKJwmWIYopAmQbiiY8F1dqMLkK2omE4RijbZbYRKKN0GPpIXHmQ9Rhfdy8iiRyRCObopGNKwqRgk1OCBgCES+aHZZaIQWmilGCyyByN
                ZZqpnnxpqhlim0/C1xWTX2b5131delknknfiOad8WwUq6KBiRmmkhIQmmuifh8o1nqONyskSZZDm6ZJRmGaqqaaPSlopo5aCKuqk1H3a0amopq
                rqqqy26uqrsG4CFBAAIfkEAQkABQAsEQAMAC8ABwCCOP/fG3BwEzVCBykrAwkSAAAAAAAAAAAACD4ACQAYSLCgwYMIEyoUOHCAw4cQI0qcSDEi
                wQIYM2rcyLGjx48gQ4ocSbKkyY0BUqpcybKly5cwY8qcSVNlQAAh+QQBCQAFACwRABEALwAYAII4/98bcHATNUIHKSsDCRIAAAAAAAAAAAAIhQ
                ABDBhIsKDBgwgTKgQgUKHDhxAHAihAsaLFixgzatzIsaPHjyBDbpzIMYDJkyhTqlzJsqXLlzBjnhQpkqFNmjgx2mSYsyfFnSR94gQqNCfRojSP
                Ii0QlKNSpDed7lz6c6rGp0uxVo1KtSJWrV2Vgu3KdOpYsmWB8kQ7Ui3bjmffemVKNSAAIfkEAQkABQAsEQAWAC8ABwCCOP/fG3BwEzVCBykrAw
                kSAAAAAAAAAAAACD4AAQwYSLCgwYMIDQJYmHAAw4YQIzpc+PAggAIYM2rcyLGjx48gQ4ocSbKkyY0BUqpcybKly5cwY8qcSVNlQAAh+QQBCQAF
                ACwRABsALwAHAII4/98bcHATNUIHKSsDCRIAAAAAAAAAAAAIPgABDBhIsKDBgwgBKFSI0CDDhhAhLnwYcQCAAhgzatzIsaPHjyBDihxJsqTJjQ
                FSqlzJsqXLlzBjypxJU2VAACH5BAEJAAUALBEAIAAvAAcAgjj/3xtwcBM1QgcpKwMJEgAAAAAAAAAAAAhDAAEMGEiQIICDBwsqRJhQocOFAh8O
                ZBgRIkKJGAcAKMCxo8ePIEOKHEmypMmTKFOq/BigpcuXMGPKnEmzps2bOF0GBAAh+QQBCQAFACwRACUALwAHAII4/98bcHATNUIHKSsDCRIAAA
                AAAAAAAAAIPwABDBhIsGBBAAgTJjTIsOFBgQ4ZKlwYsSJBAAUyatzIsaPHjyBDihxJsqTJkxwDqFzJsqXLlzBjypxJs+bKgAAh+QQBCQAFACwR
                ACoALwAHAII4/98bcHATNUIHKSsDCRIAAAAAAAAAAAAIOgABDBhIsKDBgwgTKgQgUKHDhxAHAihAsaLFixgzatzIsaPHjyBDirwYoKTJkyhTql
                zJsqXLlzBNBgQAOw==
            """,
            "ghost-glitch" to """
                R0lGODlhUABAAIIAADj/3+ZE/xM1QhsjRQMJEgAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACQAAACwAAAAAUABAAAAI/wAJCBxIsK
                DBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIUaCAhx8dhmw4kmHJhScVpky4EmHLgy8Nxiw4k2DNgTc9mhTAs6fPn0CDCh1KtGhPkiCTilSKdKnT
                plBNMpX6lGpUlFOxVtV6VWVWr1vBdmX5lWxYs2NdllV7lm1amGvhtpX7VmZcu3Px1qV5ly9Ko4ADCxa812Zfw3n9FsZ5mHFbAJAjS55MubJlx4
                sFBtjMubPnzZZDi4asMzHBz6g7D1jNurXr168jE8g5+25k2Lhzw45Mu/dCyLqDB5dtGjPC28KTxyadufZvAMqjtybO1WpC5ANSa09OHa3168Cz
                a89Hrbw7XZaDBYSXLp230czY2Zdnfr76b/nyzRu3Dx46/vkAeMffcf7hNx5qAoqlUHzsHfhZghAWxOB/u9Gn4IASFkghbvq59Z1B621YYYDFlU
                aghiKyJplvc02YInEsvjXajDPudyFMRdGoo2XpAdVcjB9GWN+NQuoVpIcYDlmkYkcqiSSRTy6J2I+NmdikkUliCaWTXGoppY1fWpklk2NOeSWZ
                W3oZ5Zpdohmmc1Sa1uOcdAIW551nmlkmmGyq2dGfgAYq6KCEFmrooYhmFBAAIfkEAQkABQAsCAAXAEAAFgCCOP/f5kT/EzVCGyNFAwkSAAAAAA
                AAAAAACMsACwgcOJCAQYIIEypcOBCAQ4YKBRicOHFhgIsYMUKE6BDARoISKVZUmLHkx4QDUnY8+XHiSpYnUw54CXOhS481P8qkmZPgTZw9Ge58
                GNSnQaJFFw4FWvSnzKcpLZbUOHBpUoEGBXSEKlPq1AAIrV4VQBbpVZQqzfYkIJHnWYJii4Zk+jZsWrow2RJwW7dA3LVt8erkGpXkV7Ans/IdTN
                jr1MRH1b79G/Rm36p3z1q+7Dfz1Z+Xuy6uCbov5aYHOTeUHJSsANWrBUMMCAAh+QQBCQAFACwMABcAPAAWAII4/9/mRP8TNUIbI0UDCRIAAAAA
                AAAAAAAI3AALFABAsKDAgwgTKlx4kIDDhwwjCixIkSLDABgzZmT40KEAiQwLDhhJcuRFjRoVFuwogADIhSJLDnhJc6VHhzQTEpQ5MydImy1x+h
                y4U+ZQiSuDCvVZtORRpASVLq3ZlGREnlYXotTokmlVkwuxglW4NePUl197Pg0ZVYBbt14B8Fwb0WZHpnPp1iXY8SxbuUb1quTbl2zZwwEEI6TY
                t7FDxIcVTyTsuPFgwE4lEwUgtWvOtJo3E5A6FLRmoBDj5hXM+KZnqpizsqbs8WlFAKEbFj56O7fAt2+PBgQAIfkEAQkABQAsDAAXADsAFgCCOP
                /f5kT/EzVCGyNFAwkSAAAAAAAAAAAACL8ACxQgQECgwYMIEypUCADAwocICT5sSLHiwgAYM2Z0CBGixIYJB4gcOfKiRo0gOz78yFGlS4EiC6R8
                GZHATJoqY97EyRLnS50tfRLc6fMh0KIDCxLFSbKpyZMYFfZEWqApyadQFwoQsJQqwqNClQb1qhAsT5tkcw6QObbj1LRl13ZNSrCuRIVQM/qMyd
                ajXbtYTzKV21blW7gGzZ6d61UxzaGF0zp+eRguX8YrxSJOTNjr3c0IMUPcCjqh6IUBAQAh+QQBCQAFACwLABcAPAAWAII4/9/mRP8TNUIbI0UD
                CRIAAAAAAAAAAAAIzQALCBRIoCCBgQgTKly4EIBDAAwjIjR4UOLAhxgdMgzAsWNHiwkpPrQ4oKRJkxs9egQZ0uBIlixPvoQ50SVEmiBlasRJ0O
                ZNnhF1/sQpcijQhUIjqly69CjSkwMeUqTItCpHpwqhlpQ6tWJEAWAF7MT6dOtYmAUFEJhJNmFSomrXtg1qkq3FtGvPzhX4lqVBtQ5LpmQqUajR
                u2kfCl5ola7Zw4gL2m3bl2bRvQMro7WJmW9dvX59YjYMtCjko5otG+yMEKPTsAJYX5wsMSAAIfkEAQkABQAsCwAXADsAFgCCOP/f5kT/EzVCGy
                NFAwkSAAAAAAAAAAAACNcACwgsAKBgwYEIEypcqJCAQ4cMIwo0eFCiRYIUDRJgGKCjR48JDQ4YOfIiQ5IkDXL8+FGhSJQmTWokIABizIEFUQ64
                eVFjTZs3X5LkadHgT6Axc8IkKnHmQ4YPoxIAsJJlR6ZOkQ6UGrWqVaYYAUjlqXQo2IhGBagVELRsybMMs25MqhNu04Jje7rdadcl3q4yAdQtoH
                OpQqseKebVK3hpYbOHEQf4+zTp3r4I5ZJtDBmzUcCWB2MOS7OyZc5v+youPfe0aLtZ2RLNODrh4rYqaw9cq/ZmQAAh+QQBCQAFACwKABgAPAAO
                AII4/9/mRP8TNUIbI0UDCRIAAAAAAAAAAAAIYQADCBxIsKDAAggTKlzIcCGBhwIeSpw40aBFgg0zakQoMSLFjwQ2ihxJsqTJkyhTqlzJsqVLlQ
                AADJhJc0DGiwFebowps6bNhjh1Ch1KtKjRo0iTIvVZ8+ZFpUxpOrW4MCAAIfkEAQkABQAsCgAXAEAAFgCCOP/f5kT/EzVCGyNFAwkSAAAAAAAA
                AAAACM8ACwgUSKBgwYEIEypcqBCAQ4cMIxYwSPHgwgAYM2aU2PAhAI4KK1JkqLEkSIEDUqZ8eFJiRZYtOapcCTHmwpcfbUqcOQCmzoE4c/5cyN
                PnUJxDIxateZQiTJ4zSZbMuFToT4oCWEJVKXVqgAJVkxYQQJYsU7EJwzYlIICAUbQD1V5lW9AqXIRyYxps+/ZuXr0F+dqNuTVl16ku9/ZtWXjA
                YZMMg96NO3NxS6STwVY+qxPz5L82JfvdzDm009JDQXcemXmgx8E6y5Jt7drj0IAAOw==
            """,
        )
    }
}
