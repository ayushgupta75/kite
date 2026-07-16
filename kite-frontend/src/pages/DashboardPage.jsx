import { Spinner } from 'react-bootstrap'
import { Navigate } from 'react-router-dom'
import KiteConnectionStatus from '../components/KiteConnectionStatus'
import { useSession } from '../context/SessionContext'

export default function DashboardPage() {
  const { loading, loggedIn, kiteConnected, kiteExpiresAt } = useSession()

  if (loading) {
    return (
      <div className="text-center">
        <Spinner animation="border" />
      </div>
    )
  }

  if (!loggedIn) {
    return <Navigate to="/login" replace />
  }

  return <KiteConnectionStatus kiteConnected={kiteConnected} kiteExpiresAt={kiteExpiresAt} />
}
