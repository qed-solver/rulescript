package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateSemiJoin(t *testing.T) {
	rule := loadRule(t, "EliminateSemiJoin")
	e := newEnv(t)
	l, _ := e.scan("nn", 1)
	r, _ := e.scan("nn", 1)
	before := e.f.ConstructSemiJoin(l, r, nil, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, l)
	u, _ := e.scan("u", 1)
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, u, nil, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, r, nil, &memo.JoinPrivate{}))
}

func TestSQLEliminateSemiJoin(t *testing.T) {
	expectSQLRepeatedScan(t, "EliminateSemiJoin", "SELECT l.a FROM nn AS l WHERE EXISTS (SELECT 1 FROM nn AS r)", "exists", "exists", false)
}
