# Publishing TavernDroid on GitHub (browser only)

This folder has two parts:

| Folder | Where it goes |
|---|---|
| `TavernDroid/` | the **repository** (source, docs, screenshots): 74 files, 3 MB |
| `Release/` | the **Releases** page: the APK (166 MB, too big for the repository upload) and its notes |

## 1. Create the repository
1. Go to <https://github.com/new>.
2. Name it, for example `TavernDroid`, and set it to **Public**.
3. Do **not** add a README, .gitignore or license (the folder already has them). Click **Create repository**.

## 2. Upload the source
1. On the new, empty repository page click **uploading an existing file**.
2. In Finder, open `TavernDroid/` and press **⌘⇧.** (Cmd+Shift+Period) so hidden files like `.gitignore` show.
3. Press **⌘A** to select everything *inside* `TavernDroid/`, then drag it onto the GitHub upload area.
   (Drag the contents, not the `TavernDroid` folder itself, or everything ends up one level too deep.)
4. Wait until all 74 files are listed, write a commit message such as *Initial release*, and click **Commit changes**.

## 3. Publish the APK
1. In the repository, click **Releases** → **Create a new release**.
2. **Choose a tag** → type `v1.0.0` → *Create new tag*.
3. Title: `TavernDroid 1.0.0`. Paste the contents of `Release/RELEASE-NOTES.md` into the description.
4. Drag `Release/TavernDroid-1.0.0.apk` into the "Attach binaries" box, wait for the upload, then **Publish release**.

## 4. Finishing touches
- Repository **About** (⚙️ on the right): description *SillyTavern as a real Android app: no Termux, no PC*;
  topics `sillytavern`, `android`, `llm`, `roleplay`, `ai-chat`, `nodejs`.
- Tell Claude the repository URL. The app's *Settings → Source code* button currently points to a placeholder;
  it will be updated and the APK rebuilt. The AGPL expects users to be able to find the source.

## Keep safe (not in this folder, never upload)
`~/AndroidProjects/TavernDroid/release.jks` and `keystore.properties` are the **signing key**. Back them up
privately: without them, future versions can't be installed as updates of this one.
