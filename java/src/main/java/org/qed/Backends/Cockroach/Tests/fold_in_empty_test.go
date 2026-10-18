package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFoldInEmpty(t *testing.T) {
	rule := loadRule(t, "FoldInEmpty")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructSemiJoin(l, e.empty(rc[0]), on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, e.empty(lc[0]))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, e.empty(rc[0]), on, &memo.JoinPrivate{}))
}
