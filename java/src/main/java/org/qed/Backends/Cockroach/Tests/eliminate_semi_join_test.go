package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateSemiJoin(t *testing.T) {
	rule := loadRule(t, "EliminateSemiJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 3)
	r, rc := e.scan("t", 3)
	before := e.f.ConstructSemiJoin(l, r, nil, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, l)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, r, nil, &memo.JoinPrivate{}))
	other, _ := e.scan("nn3", 3)
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, other, nil, &memo.JoinPrivate{}))
	filtered := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, r, filtered, &memo.JoinPrivate{}))
}
