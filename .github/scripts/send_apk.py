import asyncio, os
from pyrogram import Client

async def main():
    async with Client(
        name="session",
        api_id=int(os.environ["TG_API_ID"]),
        api_hash=os.environ["TG_API_HASH"],
        session_string=os.environ["TG_SESSION"]
    ) as app:
        await app.send_document(
            chat_id=int(os.environ["TG_CHAT_ID"]),
            document=os.environ["APK_PATH"],
            caption=f"✅ VidMax Build Done!\n📦 {os.environ['APK_NAME']}"
        )

asyncio.run(main())
