from fastmcp import FastMCP
import psutil
import platform

mcp = FastMCP("ARC-SYSTEM-SENSES")

@mcp.tool()
def get_cpu_status() -> str:
    """Retrieves current CPU load and frequency."""
    load = psutil.cpu_percent(interval=1)
    freq = psutil.cpu_freq().current
    return f"CPU Load: {load}% | Frequency: {freq:.2f}MHz"

@mcp.tool()
def get_memory_vitals() -> str:
    """Scans RAM and Swap memory usage."""
    mem = psutil.virtual_memory()
    return f"RAM Usage: {mem.percent}% ({mem.used // (1024**2)}MB used of {mem.total // (1024**2)}MB)"

@mcp.tool()
def get_disk_report() -> str:
    """Checks storage capacity on the primary partition."""
    disk = psutil.disk_usage('/')
    return f"Disk Space: {disk.percent}% used | Free: {disk.free // (1024**3)}GB"

if __name__ == "__main__":
    mcp.run()