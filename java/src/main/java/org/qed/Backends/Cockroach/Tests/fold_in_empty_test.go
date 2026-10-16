package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldInEmpty(t *testing.T) {
	rule := loadRule(t, "FoldInEmpty")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	rc := e.f.Metadata().AddColumn("empty", types.Int)
	r := e.empty(rc)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc)))
	before := e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, e.empty(lc[0]))

	rScan, _ := e.scan("t", 1)
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, rScan, nil, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, r, on, &memo.JoinPrivate{}))
}
