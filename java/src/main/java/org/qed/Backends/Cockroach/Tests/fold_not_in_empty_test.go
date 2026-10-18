package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNotInEmpty(t *testing.T) {
	rule := loadRule(t, "FoldNotInEmpty")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, _ := e.scan("u", 1)
	empty := e.f.Metadata().AddColumn("empty", types.Int)
	emptyRel := e.empty(empty)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(empty)))
	e.expectRewrite(rule, e.f.ConstructAntiJoin(l, emptyRel, on, &memo.JoinPrivate{}), l)
	e.expectRewrite(rule, e.f.ConstructAntiJoin(l, emptyRel, nil, &memo.JoinPrivate{}), l)
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, emptyRel, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, e.intValues([]opt.ColumnID{empty}, []int{1}), on, &memo.JoinPrivate{}))
}
