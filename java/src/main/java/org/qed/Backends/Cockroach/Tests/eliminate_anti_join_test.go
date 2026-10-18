package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateAntiJoin(t *testing.T) {
	rule := loadRule(t, "EliminateAntiJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	zc := e.f.Metadata().AddColumn("z", types.Int)
	empty := e.empty(zc)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(zc)))
	before := e.f.ConstructAntiJoin(l, empty, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, l)

	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, empty, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoinApply(l, empty, on, &memo.JoinPrivate{}))

	r, rc := e.scan("t", 1)
	rOn := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, r, rOn, &memo.JoinPrivate{}))

	nz := e.f.Metadata().AddColumn("nz", types.Int)
	nonEmpty := e.intValues([]opt.ColumnID{nz}, []int{1})
	nzOn := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(nz)))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, nonEmpty, nzOn, &memo.JoinPrivate{}))
}
