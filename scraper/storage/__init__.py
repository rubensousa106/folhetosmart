"""Armazenamento de folhetos PDF e do feed JSON no Cloudflare R2."""
from .r2_storage import R2Storage, r2_storage

__all__ = ["R2Storage", "r2_storage"]
