import { useState } from 'react'
import { Button, Card } from 'react-bootstrap'
import { Link, useNavigate } from 'react-router-dom'
import { login } from '../api/client'
import ErrorAlert from '../components/ErrorAlert'
import FormField from '../components/FormField'
import { useSession } from '../context/SessionContext'

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const navigate = useNavigate()
  const { refresh } = useSession()

  const handleSubmit = (e) => {
    e.preventDefault()
    setError(null)
    login(email, password)
      .then(refresh)
      .then(() => navigate('/dashboard'))
      .catch((err) => setError(err.message))
  }

  return (
    <Card>
      <Card.Body>
        <Card.Title className="mb-3">Log in</Card.Title>
        <ErrorAlert message={error} />
        <form onSubmit={handleSubmit}>
          <FormField
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <FormField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Button type="submit" variant="primary" className="w-100">
            Log in
          </Button>
        </form>
        <p className="mt-3 mb-0 text-center">
          Don't have an account? <Link to="/signup">Sign up</Link>
        </p>
      </Card.Body>
    </Card>
  )
}
