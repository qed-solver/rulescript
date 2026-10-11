package rulescript_test

import (
	"testing"
)

func TestPushFilterIntoJoinRight(t *testing.T) {
	testPushFilterIntoJoinSide(t, "PushFilterIntoJoinRight", true)
}

func TestSQLPushFilterIntoJoinRight(t *testing.T) {
	expectSQLRewrites(t, "PushFilterIntoJoinRight", "SELECT * FROM (VALUES (1)) AS l(a) JOIN (VALUES (1)) AS r(b) ON r.b>0 AND l.a=r.b")
}
