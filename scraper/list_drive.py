from storage.gdrive_storage import drive_storage

result = drive_storage.service.files().list(
    q="'" + drive_storage.folder_id + "' in parents and trashed=false",
    fields="files(id, name, createdTime)"
).execute()

files = result.get("files", [])
print("Ficheiros encontrados:", len(files))
for f in files:
    print(f["name"], "-->", f["id"])
