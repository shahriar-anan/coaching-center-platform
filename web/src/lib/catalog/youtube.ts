export function youtubeWatchUrl(videoRef: string | null): string {
  if (!videoRef) {
    return "";
  }
  return `https://www.youtube.com/watch?v=${videoRef}`;
}
