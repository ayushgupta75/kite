import { Button, Card } from 'react-bootstrap'
import { API_BASE } from '../api/client'

export default function KiteConnectionStatus({ kiteConnected, kiteExpiresAt }) {
  if (kiteConnected) {
    return (
      <Card bg="success" text="white">
        <Card.Body>
          <Card.Title>Connected to Zerodha</Card.Title>
          <Card.Text>Expires {new Date(kiteExpiresAt).toLocaleString()}</Card.Text>
        </Card.Body>
      </Card>
    )
  }

  return (
    <Card>
      <Card.Body className="text-center">
        <Card.Text>Not connected to Zerodha yet.</Card.Text>
        {/* Real browser navigation, not a fetch through api/client.js — this
            has to follow Kite's actual login page and redirect chain. */}
        <Button as="a" href={`${API_BASE}/login`} variant="primary">
          Connect to Zerodha
        </Button>
      </Card.Body>
    </Card>
  )
}
