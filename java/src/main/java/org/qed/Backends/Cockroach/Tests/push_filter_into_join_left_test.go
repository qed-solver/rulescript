package rulescript_test

import (
	"testing"
)

func TestPushFilterIntoJoinLeft(t *testing.T) {
	testPushFilterIntoJoinSide(t, "PushFilterIntoJoinLeft", false)
}

func TestSQLPushFilterIntoJoinLeft(t *testing.T) {
	expectSQLRewrites(t, "PushFilterIntoJoinLeft", "SELECT * FROM (VALUES (1)) AS l(a) JOIN (VALUES (1)) AS r(b) ON l.a>0 AND l.a=r.b")
}
