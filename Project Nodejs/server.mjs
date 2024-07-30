import express from 'express';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { parseFile } from 'music-metadata'; // Use named import for music-metadata

const app = express();
const port = 3000;

// Get directory name
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Directory containing song files
const MUSIC_DIR = path.join(__dirname, 'music');
const DATA_FILE = path.join(__dirname, 'songs.json');

// Helper function to get song data
async function getSongsFromDirectory() {
    const files = fs.readdirSync(MUSIC_DIR);
    const songs = [];
    for (const [index, file] of files.entries()) {
        const filePath = path.join(MUSIC_DIR, file);
        const metadata = await parseFile(filePath);
        const title = path.basename(file, path.extname(file));
        const artist = metadata.common.artist || "Unknown Artist";
        const duration = Math.floor(metadata.format.duration || 0);
        songs.push({
            id: index + 1,
            title: title,
            artist: artist,
            playCount: 0, // Initialize play count to 0
            duration: duration
        });
    }
    return songs;
}

// Initialize songs array
let songs = [];

// Load songs from file or directory
function loadSongs() {
    if (fs.existsSync(DATA_FILE)) {
        const data = fs.readFileSync(DATA_FILE);
        songs = JSON.parse(data);
    } else {
        getSongsFromDirectory().then(result => {
            songs = result;
            fs.writeFileSync(DATA_FILE, JSON.stringify(songs, null, 2));
        }).catch(error => {
            console.error('Error initializing songs:', error);
        });
    }
}

// Save songs to file
function saveSongs() {
    fs.writeFileSync(DATA_FILE, JSON.stringify(songs, null, 2));
}

// Load songs on server start
loadSongs();

// Middleware to parse JSON
app.use(express.json());

// Get list of songs
app.get('/songs', (req, res) => {
    res.json(songs);
});

// Get song by ID
app.get('/songs/:id', (req, res) => {
    const song = songs.find(s => s.id === parseInt(req.params.id));
    if (!song) return res.status(404).send('Song not found');
    res.json(song);
});

// Search for songs
app.get('/search', (req, res) => {
    const query = req.query.q.toLowerCase();
    const results = songs.filter(s => s.title.toLowerCase().includes(query));
    res.json(results);
});

// Update play count
app.post('/play/:id', (req, res) => {
    const song = songs.find(s => s.id === parseInt(req.params.id));
    if (!song) return res.status(404).send('Song not found');
    song.playCount++;
    saveSongs(); // Save updated play count
    res.json(song);
});

// Stream song by ID
app.get('/songs/:id/stream', (req, res) => {
    const song = songs.find(s => s.id === parseInt(req.params.id));
    if (!song) return res.status(404).send('Song not found');

    const filePath = path.join(MUSIC_DIR, song.title + '.mp3'); 
    const stat = fs.statSync(filePath);

    res.writeHead(200, {
        'Content-Type': 'audio/mpeg',
        'Content-Length': stat.size
    });

    const readStream = fs.createReadStream(filePath);
    readStream.pipe(res);
});

app.listen(port, () => {
    console.log(`Server running at http://localhost:${port}`);
});
